package com.xuejiai.aaf.module.ai.aigc.project.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.org.OrgContext;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.ai.aigc.AigcCanonicalRequest;
import com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcExecutionBindingVersionRef;
import com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcProjectInitializationSpec;
import com.xuejiai.aaf.module.ai.aigc.event.service.AigcEventPublisher;
import com.xuejiai.aaf.module.ai.aigc.execution.repository.AigcExecutionBindingRepository;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcApprovedGenerationTarget;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcGenerationProposalBridgeApi;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcGenerationProposalScope;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcGenerationProposalTargetType;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectGenerationProposalApprovalCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectGenerationProposalDraft;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectGenerationProposalRevisionCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectGenerationProposalTaskLinkCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectGenerationProposalView;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProject;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProjectConfigSnapshot;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProjectGenerationProposal;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProjectGenerationProposalItem;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcProjectConfigSnapshotRepository;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcProjectGenerationProposalItemRepository;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcProjectGenerationProposalRepository;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcProjectRepository;

import lombok.RequiredArgsConstructor;

/**
 * 项目生成 Proposal 的唯一提议与确认边界。
 *
 * <p>Proposal 只冻结用户将要确认的目标范围和目标指令；批准后的执行状态由 canonical TaskPlan 唯一管理， 本服务不创建平行 Task、Run 或 candidate
 * 状态机。
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AigcProjectGenerationProposalService implements AigcGenerationProposalBridgeApi {

    private static final String AWAITING_CONFIRMATION = "AWAITING_CONFIRMATION";
    private static final String APPROVED = "APPROVED";
    private static final String EXPIRED = "EXPIRED";

    private final AigcProjectRepository projectRepository;
    private final AigcProjectConfigSnapshotRepository snapshotRepository;
    private final AigcProjectGenerationProposalRepository proposalRepository;
    private final AigcProjectGenerationProposalItemRepository itemRepository;
    private final AigcExecutionBindingRepository bindings;
    private final OperatorContext operatorContext;
    private final AigcEventPublisher eventPublisher;

    /** 生成一个待确认 Proposal；不触发模型调用、Task、Run 或 candidate 写入。 */
    @Transactional
    public AigcProjectGenerationProposalView propose(AigcProjectGenerationProposalDraft draft) {
        Objects.requireNonNull(draft, "draft 不能为空");
        var resolved = resolveScope(draft.scope(), draft.expectedProjectVersion());
        var proposalId = "generation-proposal:" + UUID.randomUUID();
        if (resolved.creationDraft() && resolved.draftId() == null) {
            // 首次草稿提议由服务端签发身份，客户端不得自造 draftId。
            resolved = resolved.withDraftId("draft:" + UUID.randomUUID());
        }
        var items =
                normalizeItems(
                        draft.items(),
                        defaultConfirmationPolicy(draft.confirmationPolicy()),
                        resolved);
        var estimatedCredits = effectiveEstimatedCredits(draft.estimatedCredits(), items);
        var proposalVersion = 1;
        var contextHash = contextHash(resolved, items);
        var proposalHash =
                proposalHash(
                        proposalId,
                        proposalVersion,
                        resolved,
                        draft,
                        contextHash,
                        items,
                        estimatedCredits);
        var proposal = new AigcProjectGenerationProposal();
        copyScope(resolved, proposal);
        proposal.setProposalId(proposalId);
        proposal.setProposalVersion(proposalVersion);
        proposal.setProposalHash(proposalHash);
        proposal.setContextHash(contextHash);
        proposal.setStatus(AWAITING_CONFIRMATION);
        proposal.setSourceType(requireSourceType(draft.sourceType()));
        proposal.setSourceRef(trimToNull(draft.sourceRef()));
        proposal.setExpectedProjectVersion(resolved.projectVersion());
        proposal.setEstimatedCredits(estimatedCredits);
        proposal.setConfirmationPolicy(defaultConfirmationPolicy(draft.confirmationPolicy()));
        proposal.setExpiresAt(requireFutureOrNull(draft.expiresAt()));
        proposalRepository.save(proposal);
        var savedItems =
                itemRepository.saveAll(
                        items.stream()
                                .map(
                                        item ->
                                                toEntity(
                                                        proposal.getId(),
                                                        proposalId,
                                                        proposalVersion,
                                                        item))
                                .toList());
        publishChanged(proposal, "CREATED");
        return toView(proposal, savedItems.stream().map(this::toViewItem).toList());
    }

    /** 修改任何目标指令或范围时生成新版本，并令旧 Proposal hash 与确认失效。 */
    @Transactional
    public AigcProjectGenerationProposalView revise(
            AigcProjectGenerationProposalRevisionCommand command) {
        Objects.requireNonNull(command, "command 不能为空");
        var resolved = resolveScope(command.scope(), command.expectedProjectVersion());
        var previous =
                requireProposal(resolved, command.proposalId(), command.proposalVersion(), true);
        if (!Objects.equals(previous.getProposalHash(), command.proposalHash())) {
            throw new BusinessException(409, "Proposal hash 已变化，请基于最新版本修订");
        }
        if ((!AWAITING_CONFIRMATION.equals(previous.getStatus())
                        && !APPROVED.equals(previous.getStatus()))
                || previous.getLinkedTaskId() != null) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "已执行或终态 Proposal 不允许修订");
        }
        var confirmationPolicy =
                command.confirmationPolicy() == null
                        ? previous.getConfirmationPolicy()
                        : defaultConfirmationPolicy(command.confirmationPolicy());
        var items = normalizeItems(command.items(), confirmationPolicy, resolved);
        var nextVersion = previous.getProposalVersion() + 1;
        var draft =
                new AigcProjectGenerationProposalDraft(
                        command.scope(),
                        command.expectedProjectVersion(),
                        previous.getSourceType(),
                        command.sourceRef() == null ? previous.getSourceRef() : command.sourceRef(),
                        confirmationPolicy,
                        command.estimatedCredits(),
                        command.expiresAt(),
                        items);
        var contextHash = contextHash(resolved, items);
        var estimatedCredits = effectiveEstimatedCredits(draft.estimatedCredits(), items);
        var proposalHash =
                proposalHash(
                        previous.getProposalId(),
                        nextVersion,
                        resolved,
                        draft,
                        contextHash,
                        items,
                        estimatedCredits);
        previous.setStatus("SUPERSEDED");
        proposalRepository.save(previous);

        var next = new AigcProjectGenerationProposal();
        copyScope(resolved, next);
        next.setProposalId(previous.getProposalId());
        next.setProposalVersion(nextVersion);
        next.setProposalHash(proposalHash);
        next.setContextHash(contextHash);
        next.setStatus(AWAITING_CONFIRMATION);
        next.setSourceType(previous.getSourceType());
        next.setSourceRef(trimToNull(draft.sourceRef()));
        next.setExpectedProjectVersion(resolved.projectVersion());
        next.setEstimatedCredits(estimatedCredits);
        next.setConfirmationPolicy(confirmationPolicy);
        next.setExpiresAt(requireFutureOrNull(draft.expiresAt()));
        proposalRepository.save(next);
        var savedItems =
                itemRepository.saveAll(
                        items.stream()
                                .map(
                                        item ->
                                                toEntity(
                                                        next.getId(),
                                                        next.getProposalId(),
                                                        next.getProposalVersion(),
                                                        item))
                                .toList());
        publishChanged(next, "REVISED");
        return toView(next, savedItems.stream().map(this::toViewItem).toList());
    }

    /**
     * 处理表单与对话共用的结构化确认。确认后只冻结范围；#11322 创建 canonical TaskPlan 时再绑定 {@code
     * linkedTaskId}，不得在这里建立第二调度状态机。
     */
    @Transactional
    public AigcProjectGenerationProposalView approve(
            AigcProjectGenerationProposalApprovalCommand command) {
        Objects.requireNonNull(command, "command 不能为空");
        var resolved = resolveScope(command.scope(), command.expectedProjectVersion());
        var idempotencyKey = requireText(command.idempotencyKey(), "approval 幂等键不能为空");
        var approvalHash = approvalHash(command);
        var replay =
                resolved.creationDraft()
                        ? proposalRepository.findByDraftIdAndApprovalIdempotencyKey(
                                resolved.draftId(), idempotencyKey)
                        : proposalRepository.findByProjectIdAndApprovalIdempotencyKey(
                                resolved.projectId(), idempotencyKey);
        if (replay.isPresent()) {
            var previous = replay.get();
            if (!Objects.equals(previous.getApprovalRequestHash(), approvalHash)) {
                throw new BusinessException(409, "同一 Proposal approval 幂等键对应不同确认范围");
            }
            return toView(previous);
        }
        var proposal =
                requireProposal(resolved, command.proposalId(), command.proposalVersion(), true);
        if (!Objects.equals(proposal.getProposalHash(), command.proposalHash())) {
            throw new BusinessException(409, "Proposal hash 已变化，请重新确认");
        }
        if (!AWAITING_CONFIRMATION.equals(proposal.getStatus())) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "Proposal 当前不可确认");
        }
        if (proposal.getExpiresAt() != null
                && !proposal.getExpiresAt().isAfter(LocalDateTime.now())) {
            proposal.setStatus(EXPIRED);
            proposalRepository.save(proposal);
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "Proposal 已过期，请重新生成");
        }
        var items =
                itemRepository.findByProposalIdAndProposalVersionOrderByItemKeyAsc(
                        proposal.getProposalId(), proposal.getProposalVersion());
        if (!Objects.equals(command.expectedProjectVersion(), proposal.getExpectedProjectVersion())
                || !Objects.equals(
                        proposal.getContextHash(),
                        contextHashForTargetKeys(
                                resolved,
                                items.stream()
                                        .map(AigcProjectGenerationProposalItem::getItemKey)
                                        .toList()))) {
            throw new BusinessException(409, "项目结构或上下文已变化，请重新生成 Proposal");
        }
        var approvedTargetKeys = approvedTargetKeys(command.approvedTargetKeys(), items);
        proposal.setStatus(APPROVED);
        proposal.setApprovedTargetKeys(approvedTargetKeys);
        proposal.setApprovalIdempotencyKey(idempotencyKey);
        proposal.setApprovalRequestHash(approvalHash);
        proposal.setApprovedBy(operatorContext.currentOwnerId().orElseThrow());
        proposal.setApprovedByType(operatorContext.currentOperatorType().name());
        proposal.setApprovedTime(LocalDateTime.now());
        proposalRepository.save(proposal);
        publishChanged(proposal, "APPROVED");
        return toView(proposal, items.stream().map(this::toViewItem).toList());
    }

    /** 当前项目的全部 Proposal 版本投影；只读，不改变任何确认或 Task 关联状态。 */
    public List<AigcProjectGenerationProposalView> list(Long projectId) {
        var project = requireProject(projectId, null);
        return proposalRepository
                .findByProjectIdOrderByProposalIdAscProposalVersionDesc(project.getId())
                .stream()
                .map(this::toView)
                .toList();
    }

    /** 某个创建草稿下的全部 Proposal 版本投影；只返回当前用户自己的草稿。 */
    public List<AigcProjectGenerationProposalView> listDraft(String draftId) {
        var ownerId = operatorContext.currentOwnerId().orElseThrow();
        return proposalRepository
                .findByDraftIdOrderByProposalIdAscProposalVersionDesc(
                        requireText(draftId, "draftId 不能为空"))
                .stream()
                .filter(proposal -> Objects.equals(proposal.getOwnerId(), ownerId))
                .map(this::toView)
                .toList();
    }

    public AigcProjectGenerationProposalView requireApproved(
            AigcGenerationProposalScope scope,
            String proposalId,
            Integer proposalVersion,
            String proposalHash) {
        var resolved = resolveScope(scope, null);
        var proposal = requireProposal(resolved, proposalId, proposalVersion, false);
        if (!APPROVED.equals(proposal.getStatus())
                || !Objects.equals(proposal.getProposalHash(), proposalHash)) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "Proposal 未确认或 hash 已失效");
        }
        return toView(proposal);
    }

    /**
     * 按 canonical Task 反查该 Task 内的某个已确认目标。
     *
     * <p>Task 身份与受信 projectId 都来自服务端执行画像；itemKey 只用于在已批准集合内选择推进顺序，成员校验与 hash、上下文、 批准范围检查全部复用 {@link
     * #resolveApprovedTarget} 同一条链，不放宽任何一项。
     */
    @Override
    public AigcApprovedGenerationTarget resolveApprovedTargetByLinkedTask(
            String linkedTaskId, Long trustedProjectId, String itemKey) {
        var taskId = requireText(linkedTaskId, "linkedTaskId 不能为空");
        var resolvedItemKey = requireText(itemKey, "itemKey 不能为空");
        if (trustedProjectId == null || trustedProjectId < 1) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "受信 projectId 不能为空");
        }
        var proposal =
                proposalRepository
                        .findByLinkedTaskId(taskId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                GlobalErrorCode.NOT_FOUND,
                                                "当前 Task 未关联已确认生成 Proposal"));
        if (!Objects.equals(proposal.getProjectId(), trustedProjectId)) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN, "Task 关联 Proposal 与受信项目不一致");
        }
        return resolveApprovedTarget(
                AigcGenerationProposalScope.project(trustedProjectId),
                proposal.getProposalId(),
                proposal.getProposalVersion(),
                proposal.getProposalHash(),
                resolvedItemKey);
    }

    @Override
    public List<LinkedTask> linkedTasks(Long projectId, String excludedProposalId) {
        if (projectId == null || projectId < 1) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "projectId 不能为空");
        }
        return proposalRepository.findByProjectIdAndLinkedTaskIdIsNotNull(projectId).stream()
                .filter(proposal -> !Objects.equals(proposal.getProposalId(), excludedProposalId))
                .map(
                        proposal ->
                                new LinkedTask(
                                        proposal.getProposalId(),
                                        proposal.getProposalVersion(),
                                        proposal.getLinkedTaskId(),
                                        proposal.getApprovedTargetKeys()))
                .toList();
    }

    /** 解析已确认的单项目标并重新校验当前上下文；返回值只供服务端 Task/执行桥接冻结，禁止直接作为客户端输入。 */
    public AigcApprovedGenerationTarget resolveApprovedTarget(
            AigcGenerationProposalScope scope,
            String proposalId,
            Integer proposalVersion,
            String proposalHash,
            String itemKey) {
        var resolved = resolveScope(scope, null);
        var proposal = requireProposal(resolved, proposalId, proposalVersion, false);
        if (!APPROVED.equals(proposal.getStatus())
                || !Objects.equals(proposal.getProposalHash(), proposalHash)) {
            throw new BusinessException(409, "Proposal 未确认或 hash 已失效");
        }
        var items =
                itemRepository.findByProposalIdAndProposalVersionOrderByItemKeyAsc(
                        proposal.getProposalId(), proposal.getProposalVersion());
        if (!Objects.equals(
                proposal.getContextHash(),
                contextHashForTargetKeys(
                        resolved,
                        items.stream()
                                .map(AigcProjectGenerationProposalItem::getItemKey)
                                .toList()))) {
            throw new BusinessException(409, "项目结构或上下文已变化，请重新生成 Proposal");
        }
        var resolvedItemKey = requireText(itemKey, "itemKey 不能为空");
        if (!proposal.getApprovedTargetKeys().contains(resolvedItemKey)) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN, "目标不在已确认 Proposal 范围内");
        }
        var item =
                itemRepository
                        .findByProposalRecordIdAndItemKey(proposal.getId(), resolvedItemKey)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                GlobalErrorCode.NOT_FOUND, "Proposal 生成目标不存在"));
        var project = resolved.project();
        return new AigcApprovedGenerationTarget(
                resolved.kind(),
                resolved.projectId(),
                resolved.draftId(),
                resolved.blueprintVersionId(),
                resolved.projectVersion(),
                project == null ? null : project.getGraphRevision().longValue(),
                project == null ? null : project.getConfigSnapshotId(),
                project == null ? null : snapshotHash(project),
                proposal.getProposalId(),
                proposal.getProposalVersion(),
                proposal.getProposalHash(),
                proposal.getContextHash(),
                item.getItemKey(),
                AigcGenerationProposalTargetType.valueOf(item.getTargetType()),
                item.getTargetKey(),
                item.getActionKey(),
                item.getGenerationInstruction(),
                item.getInstructionHash(),
                item.getDependencyKeys(),
                item.getRequiredContextKeys(),
                item.getModelPolicy(),
                item.getEstimatedCredits(),
                item.getConfirmationPolicy(),
                item.getItemVersion(),
                item.getItemHash());
    }

    /** 只记录由上层 canonical Task 入口已成功创建的任务身份；本服务不创建、推进或查询 Task 状态。 */
    @Transactional
    public AigcProjectGenerationProposalView linkTask(
            AigcProjectGenerationProposalTaskLinkCommand command) {
        Objects.requireNonNull(command, "command 不能为空");
        var resolved = resolveScope(command.scope(), command.expectedProjectVersion());
        var proposal =
                requireProposal(resolved, command.proposalId(), command.proposalVersion(), true);
        if (!APPROVED.equals(proposal.getStatus())
                || !Objects.equals(proposal.getProposalHash(), command.proposalHash())) {
            throw new BusinessException(409, "Proposal 未确认或 hash 已失效，不能关联 Task");
        }
        var linkedTaskId = requireText(command.linkedTaskId(), "linkedTaskId 不能为空");
        if (linkedTaskId.length() > 128) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "linkedTaskId 长度不能超过 128");
        }
        if (proposal.getLinkedTaskId() != null
                && !Objects.equals(proposal.getLinkedTaskId(), linkedTaskId)) {
            throw new BusinessException(409, "Proposal 已关联其他 canonical Task");
        }
        if (proposal.getLinkedTaskId() == null) {
            proposal.setLinkedTaskId(linkedTaskId);
            proposalRepository.save(proposal);
            publishChanged(proposal, "TASK_LINKED");
        }
        return toView(proposal);
    }

    /**
     * 物化时校验并回填草稿 Proposal 与最终项目的关联。
     *
     * <p>只校验身份与授权链：APPROVED、作用域为 CREATION_DRAFT、Blueprint 版本一致、归属当前用户，且所有已确认的初始化字段目标
     * 在本次提交中确实带值。不做"字段值与提议值一致"的比对——生成结果由 canonical Task 输出并由用户在创建表单确认， Proposal 本身不持有字段值，不应伪造该真理源。
     */
    @Transactional
    public void markMaterialized(
            String draftId,
            String proposalId,
            Integer proposalVersion,
            String proposalHash,
            Long blueprintVersionId,
            Long projectId,
            java.util.Set<String> providedFieldKeys) {
        var ownerId = operatorContext.currentOwnerId().orElseThrow();
        var proposal =
                proposalRepository
                        .findForApproval(
                                requireText(proposalId, "proposalId 不能为空"), proposalVersion)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                GlobalErrorCode.NOT_FOUND, "创建草稿 Proposal 不存在"));
        if (!AigcGenerationProposalScope.Kind.CREATION_DRAFT
                .name()
                .equals(proposal.getScopeKind())) {
            throw new BusinessException(
                    GlobalErrorCode.BAD_REQUEST, "物化只接受 CREATION_DRAFT Proposal");
        }
        if (!Objects.equals(proposal.getOwnerId(), ownerId)) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN, "无权使用其他用户的创建草稿 Proposal");
        }
        if (!Objects.equals(proposal.getDraftId(), requireText(draftId, "draftId 不能为空"))) {
            throw new BusinessException(409, "创建草稿 Proposal 与本次 draftId 不一致");
        }
        if (!APPROVED.equals(proposal.getStatus())
                || !Objects.equals(proposal.getProposalHash(), proposalHash)) {
            throw new BusinessException(409, "创建草稿 Proposal 未确认或 hash 已失效");
        }
        if (!Objects.equals(proposal.getBlueprintVersionId(), blueprintVersionId)) {
            throw new BusinessException(409, "创建草稿 Proposal 与本次 Blueprint 版本不一致");
        }
        if (proposal.getLinkedProjectId() != null
                && !Objects.equals(proposal.getLinkedProjectId(), projectId)) {
            throw new BusinessException(409, "创建草稿 Proposal 已用于其他项目");
        }
        var approvedFieldKeys =
                proposal.getApprovedTargetKeys().stream()
                        .map(key -> key.substring(key.indexOf(':') + 1))
                        .toList();
        var provided = providedFieldKeys == null ? java.util.Set.<String>of() : providedFieldKeys;
        if (!provided.containsAll(approvedFieldKeys)) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "已确认的初始化字段在本次创建提交中缺少取值");
        }
        proposal.setLinkedProjectId(projectId);
        proposalRepository.save(proposal);
        publishChanged(proposal, "MATERIALIZED");
    }

    /** 发布 Proposal 变化通知：只带 projectId（创建草稿阶段为空）与状态摘要，前端据此失效 Proposal 查询。 */
    private void publishChanged(AigcProjectGenerationProposal proposal, String action) {
        if (proposal.getOwnerId() == null) {
            return;
        }
        var refs = new LinkedHashMap<String, Long>();
        refs.put(
                "projectId",
                proposal.getProjectId() != null
                        ? proposal.getProjectId()
                        : proposal.getLinkedProjectId());
        eventPublisher.publish(
                proposal.getOwnerId(),
                "proposal.changed",
                refs,
                Map.of(
                        "kind",
                        "GENERATION",
                        "action",
                        action,
                        "status",
                        proposal.getStatus(),
                        "proposalId",
                        proposal.getProposalId(),
                        "proposalVersion",
                        proposal.getProposalVersion()));
    }

    /** 直接读同聚合实体：Proposal 与项目同属 AIGC 项目聚合，避免反向依赖 {@code AigcProjectApi} 形成 Bean 环。 */
    private AigcProject requireProject(Long projectId, Integer expectedProjectVersion) {
        if (projectId == null) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "projectId 不能为空");
        }
        var project =
                projectRepository
                        .findById(projectId)
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.NOT_FOUND, "项目不存在"));
        var ownerId = operatorContext.currentOwnerId().orElseThrow();
        if (!ownerId.equals(project.getUserId())) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN, "无权操作其他用户的项目生成 Proposal");
        }
        if (expectedProjectVersion != null
                && !expectedProjectVersion.equals(project.getVersion())) {
            throw new BusinessException(409, "项目版本已变化，请重新生成 Proposal");
        }
        return project;
    }

    /** 解析作用域：PROJECT 锚定项目与版本，CREATION_DRAFT 锚定当前用户与已发布 Blueprint 版本。 */
    private ResolvedScope resolveScope(
            AigcGenerationProposalScope scope, Integer expectedProjectVersion) {
        Objects.requireNonNull(scope, "scope 不能为空");
        if (scope.creationDraft()) {
            if (expectedProjectVersion != null) {
                throw new BusinessException(
                        GlobalErrorCode.BAD_REQUEST,
                        "CREATION_DRAFT 作用域不接受 expectedProjectVersion");
            }
            var ownerId = operatorContext.currentOwnerId().orElseThrow();
            return new ResolvedScope(
                    AigcGenerationProposalScope.Kind.CREATION_DRAFT,
                    null,
                    scope.draftId(),
                    scope.blueprintVersionId(),
                    OrgContext.getCurrentOrgId(),
                    OrgContext.getCurrentWorkspaceId(),
                    ownerId);
        }
        var project = requireProject(scope.projectId(), expectedProjectVersion);
        return new ResolvedScope(
                AigcGenerationProposalScope.Kind.PROJECT,
                project,
                null,
                null,
                project.getOrgId(),
                project.getWorkspaceId(),
                project.getUserId());
    }

    /** 已持久 Proposal 必须与请求作用域完全一致，禁止跨域引用同一 proposalId。 */
    private void requireScopeMatch(AigcProjectGenerationProposal proposal, ResolvedScope scope) {
        if (!proposal.getScopeKind().equals(scope.kind().name())
                || !Objects.equals(proposal.getProjectId(), scope.projectId())
                || !Objects.equals(proposal.getDraftId(), scope.draftId())
                || !Objects.equals(proposal.getBlueprintVersionId(), scope.blueprintVersionId())) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN, "Proposal 不属于当前作用域");
        }
        if (!Objects.equals(proposal.getOwnerId(), scope.ownerId())) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN, "无权操作其他用户的生成 Proposal");
        }
    }

    private AigcProjectGenerationProposal requireProposal(
            ResolvedScope scope, String proposalId, Integer proposalVersion, boolean forUpdate) {
        var id = requireText(proposalId, "proposalId 不能为空");
        var proposal =
                (forUpdate
                                ? proposalRepository.findForApproval(id, proposalVersion)
                                : proposalRepository.findByProposalIdAndProposalVersion(
                                        id, proposalVersion))
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                GlobalErrorCode.NOT_FOUND, "项目生成 Proposal 不存在"));
        requireScopeMatch(proposal, scope);
        return proposal;
    }

    /** 服务端内部作用域解析结果；PROJECT 与 CREATION_DRAFT 字段互斥。 */
    private record ResolvedScope(
            AigcGenerationProposalScope.Kind kind,
            AigcProject project,
            String draftId,
            Long blueprintVersionId,
            Long orgId,
            Long workspaceId,
            Long ownerId) {

        private Long projectId() {
            return project == null ? null : project.getId();
        }

        private Integer projectVersion() {
            return project == null ? null : project.getVersion();
        }

        private boolean creationDraft() {
            return kind == AigcGenerationProposalScope.Kind.CREATION_DRAFT;
        }

        private ResolvedScope withDraftId(String issuedDraftId) {
            return new ResolvedScope(
                    kind, project, issuedDraftId, blueprintVersionId, orgId, workspaceId, ownerId);
        }
    }

    private List<AigcProjectGenerationProposalDraft.Item> normalizeItems(
            List<AigcProjectGenerationProposalDraft.Item> drafts,
            String defaultConfirmationPolicy,
            ResolvedScope scope) {
        if (drafts == null || drafts.isEmpty()) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "Proposal 必须至少包含一个生成目标");
        }
        var keys = new java.util.HashSet<String>();
        var normalized = new ArrayList<AigcProjectGenerationProposalDraft.Item>();
        for (var item : drafts) {
            if (item == null || item.targetType() == null) {
                throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "Proposal 目标类型不能为空");
            }
            var targetKey = requireText(item.targetKey(), "Proposal targetKey 不能为空");
            requireTargetAllowed(scope, item.targetType(), targetKey);
            var instruction =
                    requireText(
                            item.generationInstruction(), "Proposal generationInstruction 不能为空");
            var itemKey = item.targetType().name() + ":" + targetKey;
            if (itemKey.length() > 128 || !keys.add(itemKey)) {
                throw new BusinessException(
                        GlobalErrorCode.BAD_REQUEST, "Proposal 目标重复或键过长: " + itemKey);
            }
            if (item.estimatedCredits() != null) {
                throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "生成成本由服务端执行绑定决定，不接受外部声明");
            }
            var normalizedActionKey = trimToNull(item.actionKey());
            normalized.add(
                    new AigcProjectGenerationProposalDraft.Item(
                            item.targetType(),
                            targetKey,
                            normalizedActionKey,
                            instruction,
                            normalizedKeys(item.dependencyKeys(), "dependencyKeys"),
                            normalizedKeys(item.requiredContextKeys(), "requiredContextKeys"),
                            trimToNull(item.modelPolicy()),
                            resolveActionCredits(scope, normalizedActionKey),
                            item.confirmationPolicy() == null
                                    ? defaultConfirmationPolicy
                                    : defaultConfirmationPolicy(item.confirmationPolicy())));
        }
        return normalized.stream()
                .sorted(
                        Comparator.comparing(
                                item -> item.targetType().name() + ":" + item.targetKey()))
                .toList();
    }

    private AigcProjectGenerationProposalItem toEntity(
            Long proposalRecordId,
            String proposalId,
            Integer proposalVersion,
            AigcProjectGenerationProposalDraft.Item item) {
        var itemKey = item.targetType().name() + ":" + item.targetKey();
        var entity = new AigcProjectGenerationProposalItem();
        entity.setProposalRecordId(proposalRecordId);
        entity.setProposalId(proposalId);
        entity.setProposalVersion(proposalVersion);
        entity.setItemKey(itemKey);
        entity.setTargetType(item.targetType().name());
        entity.setTargetKey(item.targetKey());
        entity.setActionKey(item.actionKey());
        entity.setGenerationInstruction(item.generationInstruction());
        entity.setInstructionHash(instructionHash(item.generationInstruction()));
        entity.setDependencyKeys(item.dependencyKeys());
        entity.setRequiredContextKeys(item.requiredContextKeys());
        entity.setModelPolicy(item.modelPolicy());
        entity.setEstimatedCredits(item.estimatedCredits());
        entity.setConfirmationPolicy(item.confirmationPolicy());
        entity.setItemVersion(proposalVersion);
        entity.setItemHash(itemHash(item));
        return entity;
    }

    /** 草稿域只允许初始化字段目标，且字段键必须属于服务端固定字段目录；项目域沿用既有类型范围。 */
    private void requireTargetAllowed(
            ResolvedScope scope, AigcGenerationProposalTargetType targetType, String targetKey) {
        if (!scope.creationDraft()) {
            return;
        }
        if (targetType != AigcGenerationProposalTargetType.INITIALIZATION_FIELD) {
            throw new BusinessException(
                    GlobalErrorCode.BAD_REQUEST, "物化前草稿只允许 INITIALIZATION_FIELD 目标：" + targetType);
        }
        if (!AigcProjectInitializationSpec.supportedFieldKeys().contains(targetKey)) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "未知初始化字段键：" + targetKey);
        }
    }

    private String contextHash(
            ResolvedScope scope, List<AigcProjectGenerationProposalDraft.Item> items) {
        return contextHashForTargetKeys(
                scope,
                items.stream()
                        .map(item -> item.targetType().name() + ":" + item.targetKey())
                        .toList());
    }

    /**
     * 上下文摘要按作用域取不同锚点：项目域锚定项目版本、快照与 graphRevision；草稿域锚定 owner 与已发布 Blueprint 版本。
     * 两者都纳入目标键集合，任一变化都要求重新提议。
     */
    private String contextHashForTargetKeys(ResolvedScope scope, List<String> targetKeys) {
        var context = new LinkedHashMap<String, Object>();
        context.put("scopeKind", scope.kind().name());
        if (scope.creationDraft()) {
            context.put("draftId", scope.draftId());
            context.put("blueprintVersionId", scope.blueprintVersionId());
            context.put("ownerId", scope.ownerId());
        } else {
            var project = scope.project();
            context.put("projectId", project.getId());
            context.put("projectVersion", project.getVersion());
            context.put("graphRevision", project.getGraphRevision().longValue());
            context.put("configurationSnapshotId", project.getConfigSnapshotId());
            context.put("configurationSnapshotHash", snapshotHash(project));
        }
        context.put("targetKeys", targetKeys.stream().sorted().toList());
        return AigcCanonicalRequest.canonicalSha256(context);
    }

    /** 项目当前配置快照哈希；快照 insert-only，此处只读不改变任何真理源。 */
    private String snapshotHash(AigcProject project) {
        return snapshotRepository
                .findById(project.getConfigSnapshotId())
                .map(AigcProjectConfigSnapshot::getSnapshotHash)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "项目配置快照不存在"));
    }

    private String proposalHash(
            String proposalId,
            Integer proposalVersion,
            ResolvedScope scope,
            AigcProjectGenerationProposalDraft draft,
            String contextHash,
            List<AigcProjectGenerationProposalDraft.Item> items,
            BigDecimal estimatedCredits) {
        var proposal = new LinkedHashMap<String, Object>();
        proposal.put("proposalId", proposalId);
        proposal.put("proposalVersion", proposalVersion);
        proposal.put("scopeKind", scope.kind().name());
        proposal.put("projectId", scope.projectId());
        proposal.put("draftId", scope.draftId());
        proposal.put("blueprintVersionId", scope.blueprintVersionId());
        proposal.put("sourceType", requireSourceType(draft.sourceType()));
        proposal.put("sourceRef", trimToNull(draft.sourceRef()));
        proposal.put("contextHash", contextHash);
        proposal.put("confirmationPolicy", defaultConfirmationPolicy(draft.confirmationPolicy()));
        proposal.put("estimatedCredits", estimatedCredits);
        proposal.put("expiresAt", draft.expiresAt());
        proposal.put("itemHashes", items.stream().map(this::itemHash).toList());
        return AigcCanonicalRequest.canonicalSha256(proposal);
    }

    private String instructionHash(String instruction) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(instruction.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("运行环境不支持 SHA-256", failure);
        }
    }

    private String itemHash(AigcProjectGenerationProposalDraft.Item item) {
        var value = new LinkedHashMap<String, Object>();
        value.put("targetType", item.targetType().name());
        value.put("targetKey", item.targetKey());
        value.put("actionKey", item.actionKey());
        value.put("generationInstruction", item.generationInstruction());
        value.put("dependencyKeys", item.dependencyKeys());
        value.put("requiredContextKeys", item.requiredContextKeys());
        value.put("modelPolicy", item.modelPolicy());
        value.put("estimatedCredits", item.estimatedCredits());
        value.put("confirmationPolicy", item.confirmationPolicy());
        return AigcCanonicalRequest.canonicalSha256(value);
    }

    private String approvalHash(AigcProjectGenerationProposalApprovalCommand command) {
        var value = new LinkedHashMap<String, Object>();
        value.put("scopeKind", command.scope().kind().name());
        value.put("projectId", command.scope().projectId());
        value.put("draftId", command.scope().draftId());
        value.put("blueprintVersionId", command.scope().blueprintVersionId());
        value.put("proposalId", command.proposalId());
        value.put("proposalVersion", command.proposalVersion());
        value.put("proposalHash", command.proposalHash());
        value.put("expectedProjectVersion", command.expectedProjectVersion());
        value.put(
                "approvedTargetKeys",
                normalizedKeys(command.approvedTargetKeys(), "approvedTargetKeys"));
        return AigcCanonicalRequest.canonicalSha256(value);
    }

    private List<String> approvedTargetKeys(
            List<String> requested, List<AigcProjectGenerationProposalItem> items) {
        var approved = normalizedKeys(requested, "approvedTargetKeys");
        if (approved.isEmpty()) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "必须显式确认至少一个生成目标");
        }
        var available =
                items.stream()
                        .map(AigcProjectGenerationProposalItem::getItemKey)
                        .collect(Collectors.toSet());
        if (!available.containsAll(approved)) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "确认范围包含未提议的生成目标");
        }
        return approved;
    }

    private List<String> normalizedKeys(List<String> values, String field) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        var normalized =
                values.stream()
                        .map(value -> requireText(value, field + " 不能包含空值"))
                        .sorted()
                        .toList();
        if (normalized.stream().distinct().count() != normalized.size()) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, field + " 不能重复");
        }
        return normalized;
    }

    private String requireSourceType(String sourceType) {
        var value = requireText(sourceType, "Proposal sourceType 不能为空").toUpperCase();
        if (!Set.of("ASSISTANT", "USER", "SYSTEM").contains(value)) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "Proposal sourceType 非法");
        }
        return value;
    }

    private String defaultConfirmationPolicy(String value) {
        var policy =
                value == null
                        ? "REQUIRED"
                        : requireText(value, "confirmationPolicy 不能为空").toUpperCase();
        if (!Set.of("REQUIRED", "OPTIONAL").contains(policy)) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "confirmationPolicy 非法");
        }
        return policy;
    }

    private BigDecimal effectiveEstimatedCredits(
            BigDecimal requested, List<AigcProjectGenerationProposalDraft.Item> items) {
        if (requested != null) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "生成成本由服务端执行绑定决定，不接受外部声明");
        }
        return items.stream()
                .map(AigcProjectGenerationProposalDraft.Item::estimatedCredits)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 单项目标的服务端权威预估成本。
     *
     * <p>口径固定为项目配置快照冻结的执行绑定版本上的 {@code estimatedCredits}，与动作真实提交时 {@code
     * AigcExecutionRun.costCredits} 取同一来源。无 actionKey、物化前草稿域或该动作没有绑定时返回 {@code null}——不编造数字。
     */
    private BigDecimal resolveActionCredits(ResolvedScope scope, String actionKey) {
        if (actionKey == null || scope.project() == null) {
            return null;
        }
        var bindingActionKey =
                "project.cover.generate".equals(actionKey) ? "image.generate" : actionKey;
        var snapshot =
                snapshotRepository
                        .findById(scope.project().getConfigSnapshotId())
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                GlobalErrorCode.NOT_FOUND, "项目配置快照不存在"));
        var bindingVersionId =
                snapshot.getSnapshot().projectSpecification().executionBindings().stream()
                        .filter(reference -> bindingActionKey.equals(reference.actionKey()))
                        .map(AigcExecutionBindingVersionRef::executionBindingVersionId)
                        .findFirst()
                        .orElse(null);
        if (bindingVersionId == null) {
            return null;
        }
        var binding =
                bindings.findById(bindingVersionId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                GlobalErrorCode.BAD_REQUEST,
                                                "项目固定的动作执行绑定不存在: " + bindingActionKey));
        if (!"published".equals(binding.getStatus())
                || !bindingActionKey.equals(binding.getActionKey())) {
            throw new BusinessException(
                    GlobalErrorCode.BAD_REQUEST, "项目固定的动作执行绑定无效: " + bindingActionKey);
        }
        return binding.getEstimatedCredits();
    }

    private LocalDateTime requireFutureOrNull(LocalDateTime expiresAt) {
        if (expiresAt != null && !expiresAt.isAfter(LocalDateTime.now())) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "Proposal expiresAt 必须晚于当前时间");
        }
        return expiresAt;
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, message);
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void copyScope(ResolvedScope scope, AigcProjectGenerationProposal proposal) {
        proposal.setScopeKind(scope.kind().name());
        proposal.setProjectId(scope.projectId());
        proposal.setDraftId(scope.draftId());
        proposal.setBlueprintVersionId(scope.blueprintVersionId());
        proposal.setOrgId(scope.orgId());
        proposal.setWorkspaceId(scope.workspaceId());
        proposal.setOwnerId(scope.ownerId());
    }

    private AigcProjectGenerationProposalView toView(AigcProjectGenerationProposal proposal) {
        var items =
                itemRepository
                        .findByProposalIdAndProposalVersionOrderByItemKeyAsc(
                                proposal.getProposalId(), proposal.getProposalVersion())
                        .stream()
                        .map(this::toViewItem)
                        .toList();
        return toView(proposal, items);
    }

    private AigcProjectGenerationProposalView toView(
            AigcProjectGenerationProposal proposal,
            List<AigcProjectGenerationProposalView.Item> items) {
        return new AigcProjectGenerationProposalView(
                AigcGenerationProposalScope.Kind.valueOf(proposal.getScopeKind()),
                proposal.getProjectId(),
                proposal.getDraftId(),
                proposal.getBlueprintVersionId(),
                proposal.getLinkedProjectId(),
                proposal.getProposalId(),
                proposal.getProposalVersion(),
                proposal.getProposalHash(),
                proposal.getContextHash(),
                proposal.getStatus(),
                proposal.getConfirmationPolicy(),
                proposal.getEstimatedCredits(),
                proposal.getApprovedTargetKeys(),
                proposal.getLinkedTaskId(),
                proposal.getExpiresAt(),
                items);
    }

    private AigcProjectGenerationProposalView.Item toViewItem(
            AigcProjectGenerationProposalItem item) {
        return new AigcProjectGenerationProposalView.Item(
                item.getItemKey(),
                AigcGenerationProposalTargetType.valueOf(item.getTargetType()),
                item.getTargetKey(),
                item.getActionKey(),
                item.getGenerationInstruction(),
                item.getInstructionHash(),
                item.getDependencyKeys(),
                item.getRequiredContextKeys(),
                item.getModelPolicy(),
                item.getEstimatedCredits(),
                item.getConfirmationPolicy(),
                item.getItemVersion(),
                item.getItemHash());
    }
}
