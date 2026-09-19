package com.xuejiai.aaf.module.ai.aigc.project.service;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcApprovedGenerationTarget;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcGenerationProposalScope;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectApi;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectConversationContext;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectGenerationProposalApprovalCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectGenerationProposalTaskLinkCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectGenerationProposalView;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectGenerationStartCommand;
import com.xuejiai.aaf.module.ai.assistant.port.ProgrammaticAssistantTaskPort;

/**
 * 已确认 Proposal 到 canonical Task 的唯一物化边界。
 *
 * <p>单一原子操作：结构化确认 → 通过 Assistant 受控程序化入口创建 canonical Task → 成功后才写入 {@code linkedTaskId}。 taskId
 * 由项目、Proposal 身份、版本与 hash 确定性派生，重放返回同一 Task；本服务不拼装 {@code AssistantCommand}、不创建第二个
 * Task/Run/candidate 状态机，也不管理 Task 状态。
 *
 * <p>三步各自使用自己的事务边界，故意不包成一个数据库事务：canonical Task 由框架在独立事务内物化，若外层回滚会留下"已创建 Task 但未关联"的悬挂状态；确定性 taskId
 * 使重放收敛到同一 Task，因此中断后重试即可补齐 {@code linkedTaskId}。
 */
@Service
public class AigcProjectGenerationMaterializationService {

    /** 项目受控生成固定路由：内容创作者 Role + 项目内容协作 Skill，由 seed 发布并由 Assistant 侧重新校验。 */
    static final String PROJECT_COLLABORATION_ROLE_KEY = "system.role.content-creator";

    static final String PROJECT_COLLABORATION_SKILL_KEY =
            "system.skill.content-project-collaboration";

    private final AigcProjectApi projects;
    private final AigcProjectGenerationProposalService proposals;
    private final ProgrammaticAssistantTaskPort assistantTasks;

    public AigcProjectGenerationMaterializationService(
            AigcProjectApi projects,
            AigcProjectGenerationProposalService proposals,
            ProgrammaticAssistantTaskPort assistantTasks) {
        this.projects = Objects.requireNonNull(projects, "projects 不能为空");
        this.proposals = Objects.requireNonNull(proposals, "proposals 不能为空");
        this.assistantTasks = Objects.requireNonNull(assistantTasks, "assistantTasks 不能为空");
    }

    /** 确认并启动单项已确认生成目标；三步顺序固定，任一步失败都不留下已关联但不存在的 Task。 */
    public AigcProjectGenerationProposalView approveAndStart(
            AigcProjectGenerationStartCommand command) {
        Objects.requireNonNull(command, "command 不能为空");
        var scope = command.scope();
        var approved =
                proposals.approve(
                        new AigcProjectGenerationProposalApprovalCommand(
                                scope,
                                command.proposalId(),
                                command.proposalVersion(),
                                command.proposalHash(),
                                command.expectedProjectVersion(),
                                command.approvedTargetKeys(),
                                command.approvalIdempotencyKey()));
        if (approved.approvedTargetKeys().size() != 1) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "受控生成入口一次只接受单项已确认目标");
        }
        var target =
                proposals.resolveApprovedTarget(
                        scope,
                        approved.proposalId(),
                        approved.proposalVersion(),
                        approved.proposalHash(),
                        approved.approvedTargetKeys().getFirst());
        var assistantThread = assistantThread(scope, command.assistantId());
        var receipt =
                assistantTasks.submitTask(
                        new ProgrammaticAssistantTaskPort.Request(
                                assistantThread.assistantId(),
                                assistantThread.threadId(),
                                taskIdempotencyKey(target),
                                objective(target)));
        return proposals.linkTask(
                new AigcProjectGenerationProposalTaskLinkCommand(
                        scope,
                        approved.proposalId(),
                        approved.proposalVersion(),
                        approved.proposalHash(),
                        command.expectedProjectVersion(),
                        receipt.taskId()));
    }

    /**
     * 解析任务运行身份。
     *
     * <p>项目域必须使用服务端受控项目会话（{@code READY}），客户端不得指定 Assistant；草稿域尚无项目会话，因此要求显式选择 Assistant 并固定在 {@code
     * draft:{draftId}} 这条草稿 thread 上运行——该 thread 物化后不迁移为项目会话，避免形成项目身份的第二来源。
     */
    private AssistantThread assistantThread(AigcGenerationProposalScope scope, String assistantId) {
        if (scope.creationDraft()) {
            var explicitAssistantId = assistantId == null ? null : assistantId.trim();
            if (explicitAssistantId == null || explicitAssistantId.isEmpty()) {
                throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "物化前生成必须显式指定 Assistant");
            }
            if (scope.draftId() == null) {
                throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "草稿域必须携带 draftId");
            }
            return new AssistantThread(explicitAssistantId, scope.draftId());
        }
        if (assistantId != null && !assistantId.isBlank()) {
            throw new BusinessException(
                    GlobalErrorCode.BAD_REQUEST, "项目域使用服务端受控会话，不接受客户端指定 Assistant");
        }
        var conversation = projects.conversationContext(scope.projectId());
        if (conversation.state() != AigcProjectConversationContext.State.READY) {
            throw new BusinessException(409, "项目尚未绑定可执行 Assistant 会话，无法启动生成任务");
        }
        return new AssistantThread(conversation.assistantId(), conversation.threadId());
    }

    private record AssistantThread(String assistantId, String threadId) {}

    /** canonical Task 身份只由作用域与 Proposal 身份决定，不含时间戳或随机值。 */
    private static String taskIdempotencyKey(AigcApprovedGenerationTarget target) {
        return "aigc-project-generation|"
                + target.scopeKind().name()
                + '|'
                + (target.scopeKind() == AigcGenerationProposalScope.Kind.CREATION_DRAFT
                        ? target.draftId()
                        : target.projectId())
                + '|'
                + target.proposalId()
                + '|'
                + target.proposalVersion()
                + '|'
                + target.proposalHash();
    }

    private static ProgrammaticAssistantTaskPort.Objective objective(
            AigcApprovedGenerationTarget target) {
        var summary =
                target.scopeKind() == AigcGenerationProposalScope.Kind.CREATION_DRAFT
                        ? "为创建草稿 %s 生成初始化字段 %s 的建议内容。"
                                .formatted(target.draftId(), target.targetKey())
                        : "完成项目 %d 已确认生成目标 %s（%s/%s）。"
                                .formatted(
                                        target.projectId(),
                                        target.itemKey(),
                                        target.targetType().name(),
                                        target.targetKey());
        return new ProgrammaticAssistantTaskPort.Objective(
                summary,
                target.generationInstruction(),
                proposalItemRef(target),
                PROJECT_COLLABORATION_ROLE_KEY,
                PROJECT_COLLABORATION_SKILL_KEY,
                List.of(targetFact(target)));
    }

    private static String proposalItemRef(AigcApprovedGenerationTarget target) {
        return "proposal:%s:%d:%s"
                .formatted(target.proposalId(), target.proposalVersion(), target.itemKey());
    }

    /** 服务端已冻结的目标事实；作为不可信只读材料进入上下文，不授予权限也不替代运行期 readiness 校验。 */
    private static ProgrammaticAssistantTaskPort.Fact targetFact(
            AigcApprovedGenerationTarget target) {
        var text =
                new StringBuilder()
                        .append("scopeKind=")
                        .append(target.scopeKind().name())
                        .append("\nprojectId=")
                        .append(target.projectId())
                        .append("\ndraftId=")
                        .append(target.draftId())
                        .append("\nblueprintVersionId=")
                        .append(target.blueprintVersionId())
                        .append("\nprojectVersion=")
                        .append(target.projectVersion())
                        .append("\ngraphRevision=")
                        .append(target.graphRevision())
                        .append("\nconfigurationSnapshotHash=")
                        .append(target.configurationSnapshotHash())
                        .append("\nproposalHash=")
                        .append(target.proposalHash())
                        .append("\ncontextHash=")
                        .append(target.contextHash())
                        .append("\nitemKey=")
                        .append(target.itemKey())
                        .append("\ntargetType=")
                        .append(target.targetType().name())
                        .append("\ntargetKey=")
                        .append(target.targetKey())
                        .append("\nactionKey=")
                        .append(target.actionKey())
                        .append("\ninstructionHash=")
                        .append(target.instructionHash())
                        .append("\ndependencyKeys=")
                        .append(target.dependencyKeys())
                        .append("\nrequiredContextKeys=")
                        .append(target.requiredContextKeys())
                        .append("\nmodelPolicy=")
                        .append(target.modelPolicy())
                        .append("\nestimatedCredits=")
                        .append(target.estimatedCredits())
                        .append("\nconfirmationPolicy=")
                        .append(target.confirmationPolicy())
                        .append("\nitemVersion=")
                        .append(target.itemVersion())
                        .append("\nitemHash=")
                        .append(target.itemHash())
                        .toString();
        return new ProgrammaticAssistantTaskPort.Fact(
                "aigc.project.generation.target",
                String.valueOf(target.proposalVersion()),
                "已确认生成目标的冻结范围与哈希",
                text);
    }
}
