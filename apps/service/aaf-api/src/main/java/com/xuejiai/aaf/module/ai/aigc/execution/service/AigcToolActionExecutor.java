package com.xuejiai.aaf.module.ai.aigc.execution.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.module.ai.aigc.execution.api.AigcRuntimeExecution.Command;
import com.xuejiai.aaf.module.ai.aigc.execution.api.AigcExecutionRunStatus;
import com.xuejiai.aaf.module.ai.aigc.execution.api.AigcToolExecutionPort;
import com.xuejiai.aaf.module.ai.aigc.execution.domain.AigcExecutionRun;
import com.xuejiai.aaf.module.ai.aigc.execution.domain.AigcExecutionTaskRef;
import com.xuejiai.aaf.module.ai.aigc.execution.repository.AigcExecutionRunRepository;
import com.xuejiai.aaf.module.ai.aigc.execution.repository.AigcExecutionTaskRefRepository;
import com.xuejiai.aaf.module.ai.aigc.project.event.AigcExecutionCandidateProducedEvent;
import com.xuejiai.aaf.module.ai.aigc.project.event.AigcObjectCandidatePayload;

import lombok.RequiredArgsConstructor;

/** Tool 动作执行器：经真实运行端口调用专业能力，并统一维护 Run 与候选事件。 */
@Component
@RequiredArgsConstructor
public class AigcToolActionExecutor implements AigcActionExecutor {

    private final AigcToolExecutionPort executionPort;
    private final AigcExecutionRunRepository runRepository;
    private final AigcExecutionTaskRefRepository taskRefRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public boolean supports(String targetType) {
        return "tool".equals(targetType);
    }

    @Override
    public void execute(AigcActionContext context) {
        var run = markRunning(context.executionRun());
        var result = executionPort.execute(toCommand(context));
        if (result.asynchronous()) {
            persistTaskReferences(run, result.taskIds());
            run.setOutputPayload(Map.of("taskIds", result.taskIds()));
            runRepository.save(run);
            return;
        }
        persistCandidate(run, result.output());
    }

    private Command toCommand(AigcActionContext context) {
        var run = context.executionRun();
        var project = context.project();
        var prompt = context.command().prompt();
        return new Command(
                run.getId(),
                run.getProjectId(),
                run.getObjectId(),
                project.orgId(),
                project.workspaceId(),
                project.userId(),
                context.binding().getTargetRef(),
                run.getActionKey(),
                prompt == null || prompt.isBlank() ? project.brief() : prompt,
                context.command().idempotencyKey(),
                run.getEffectiveInput());
    }

    private AigcExecutionRun markRunning(AigcExecutionRun run) {
        run.setStatus(AigcExecutionRunStatus.RUNNING);
        run.setStartTime(LocalDateTime.now());
        run.setVersion(run.getVersion() + 1);
        return runRepository.save(run);
    }

    private void persistTaskReferences(AigcExecutionRun run, List<Long> taskIds) {
        for (var index = 0; index < taskIds.size(); index++) {
            var reference = new AigcExecutionTaskRef();
            reference.setExecutionRunId(run.getId());
            reference.setTaskId(taskIds.get(index));
            reference.setRole("tool-output");
            reference.setSortOrder(index);
            taskRefRepository.save(reference);
        }
    }

    private void persistCandidate(AigcExecutionRun run, String output) {
        run.setOutputPayload(Map.of("candidateProduced", true, "output", output));
        runRepository.save(run);
        eventPublisher.publishEvent(
                new AigcExecutionCandidateProducedEvent(
                        UUID.randomUUID(),
                        run.getId(),
                        run.getExecutionSubmissionId(),
                        run.getExecutionReservationId(),
                        run.getRootExecutionRunId(),
                        run.getProjectId(),
                        run.getObjectId(),
                        List.of(
                                new AigcObjectCandidatePayload(
                                        JsonUtils.toJsonString(
                                                Map.of(
                                                        "text",
                                                        output,
                                                        "actionKey",
                                                        run.getActionKey())),
                                        null,
                                        List.of())),
                        List.of(),
                        Instant.now()));
    }
}
