package com.xuejiai.aaf.module.ai.aigc.project.service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.module.ai.aigc.project.api.AssistantBlueprintCreationContext;
import com.xuejiai.aaf.module.ai.aigc.project.api.AssistantBlueprintProposalCommands;
import com.xuejiai.aaf.module.ai.aigc.project.api.AssistantBlueprintProposalView;
import com.xuejiai.aaf.module.ai.assistant.port.ProgrammaticAssistantTaskPort;

/** Assistant Blueprint 规划任务编排；Proposal 先提交，再允许 Task 执行 Tool。 */
@Service
public class AssistantBlueprintPlanningService {

    private final AssistantBlueprintProposalService proposals;
    private final ProgrammaticAssistantTaskPort assistantTasks;

    public AssistantBlueprintPlanningService(
            AssistantBlueprintProposalService proposals,
            ProgrammaticAssistantTaskPort assistantTasks) {
        this.proposals = Objects.requireNonNull(proposals, "proposals 不能为空");
        this.assistantTasks = Objects.requireNonNull(assistantTasks, "assistantTasks 不能为空");
    }

    public AssistantBlueprintProposalView start(AssistantBlueprintProposalCommands.Start command) {
        Objects.requireNonNull(command, "command 不能为空");
        var planningId = UUID.randomUUID().toString();
        var proposalId = UUID.randomUUID().toString();
        var creationContext = proposals.freezeCreationContext(command.creationContext());
        var request =
                taskRequest(
                        command.assistantId(),
                        planningId,
                        proposalId,
                        1,
                        creationContext,
                        command.instruction());
        var taskIdentity = assistantTasks.identifyTask(request);
        var proposal =
                proposals.createPlaceholder(
                        planningId,
                        proposalId,
                        creationContext,
                        command.instruction(),
                        command.assistantId(),
                        taskIdentity.taskId());
        proposals.authorizePlanningTask(taskIdentity.taskId());
        submit(request, taskIdentity.taskId());
        return proposal;
    }

    public AssistantBlueprintProposalView revise(
            AssistantBlueprintProposalCommands.Revise command) {
        Objects.requireNonNull(command, "command 不能为空");
        var seed = proposals.revisionSeed(command);
        var planningId = UUID.randomUUID().toString();
        var request =
                taskRequest(
                        seed.assistantId(),
                        planningId,
                        seed.proposalId(),
                        seed.proposalVersion(),
                        seed.creationContext(),
                        command.instruction());
        var taskIdentity = assistantTasks.identifyTask(request);
        var proposal = proposals.createRevision(command, planningId, taskIdentity.taskId());
        proposals.authorizePlanningTask(taskIdentity.taskId());
        submit(request, taskIdentity.taskId());
        return proposal;
    }

    public AssistantBlueprintProposalView cancel(
            AssistantBlueprintProposalCommands.Cancel command) {
        var proposal = proposals.cancel(command);
        assistantTasks.cancelTask(proposal.sourceTaskId(), "Blueprint Proposal 已取消");
        return proposal;
    }

    private void submit(ProgrammaticAssistantTaskPort.Request request, String expectedTaskId) {
        try {
            var receipt = assistantTasks.submitTask(request);
            if (!expectedTaskId.equals(receipt.taskId())) {
                proposals.markPlanningFailed(expectedTaskId, "canonical Task 身份与预绑定不一致");
                throw new IllegalStateException("canonical Task 身份与预绑定不一致");
            }
        } catch (RuntimeException failure) {
            proposals.markPlanningFailed(expectedTaskId, safeMessage(failure));
            throw failure;
        }
    }

    private ProgrammaticAssistantTaskPort.Request taskRequest(
            String assistantId,
            String planningId,
            String proposalId,
            Integer proposalVersion,
            AssistantBlueprintCreationContext creationContext,
            String instruction) {
        var factText =
                JsonUtils.toJsonString(
                        java.util.Map.of(
                                "creationContext", creationContext,
                                "planningInstruction", instruction));
        var objective =
                new ProgrammaticAssistantTaskPort.Objective(
                        "为项目“%s”生成可物化的逻辑 Blueprint Proposal。".formatted(creationContext.name()),
                        null,
                        null,
                        ProgrammaticAssistantTaskPort.Purpose.BLUEPRINT_PLANNING.roleKey(),
                        ProgrammaticAssistantTaskPort.Purpose.BLUEPRINT_PLANNING.skillKey(),
                        List.of(
                                new ProgrammaticAssistantTaskPort.Fact(
                                        "aigc.blueprint.planning.request",
                                        String.valueOf(proposalVersion),
                                        "冻结创建上下文：类型=%s，渠道=%s"
                                                .formatted(
                                                        creationContext
                                                                .projectSpecification()
                                                                .projectTypeCode(),
                                                        creationContext
                                                                .projectSpecification()
                                                                .channelCodes()),
                                        factText)));
        return new ProgrammaticAssistantTaskPort.Request(
                assistantId,
                "blueprint-planning:" + planningId,
                "assistant-blueprint-planning|" + planningId,
                ProgrammaticAssistantTaskPort.Purpose.BLUEPRINT_PLANNING,
                null,
                objective);
    }

    private static String safeMessage(RuntimeException failure) {
        var message = failure.getMessage();
        if (message == null || message.isBlank()) {
            return failure.getClass().getSimpleName();
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
    }
}
