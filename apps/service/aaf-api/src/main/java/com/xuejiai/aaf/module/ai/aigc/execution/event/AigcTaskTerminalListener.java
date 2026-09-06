package com.xuejiai.aaf.module.ai.aigc.execution.event;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.xuejiai.aaf.module.ai.aigc.execution.domain.AigcExecutionRun;
import com.xuejiai.aaf.module.ai.aigc.execution.repository.AigcExecutionRunRepository;
import com.xuejiai.aaf.module.ai.aigc.execution.repository.AigcExecutionTaskRefRepository;
import com.xuejiai.aaf.module.ai.aigc.execution.service.AigcExecutionTerminalService;
import com.xuejiai.aaf.module.ai.aigc.media.api.AigcMediaApi;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectApi;
import com.xuejiai.aaf.module.ai.aigc.project.event.AigcExecutionCandidateProducedEvent;
import com.xuejiai.aaf.module.ai.aigc.task.event.AigcTaskTerminalEvent;

import lombok.RequiredArgsConstructor;

/** 汇总媒体子任务终态；项目封面通过明确命令回填，其他结果发布候选。 */
@Component
@RequiredArgsConstructor
public class AigcTaskTerminalListener {

    private final AigcExecutionTaskRefRepository taskRefRepository;
    private final AigcExecutionRunRepository runRepository;
    private final AigcProjectApi projectApi;
    private final AigcMediaApi mediaApi;
    private final AigcExecutionTerminalService terminalService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onTerminal(AigcTaskTerminalEvent event) {
        var reference =
                taskRefRepository
                        .findFirstByTaskIdAndDeletedFalseOrderByIdDesc(event.taskId())
                        .orElse(null);
        if (reference == null) {
            return;
        }
        var snapshot = runRepository.findById(reference.getExecutionRunId()).orElse(null);
        if (snapshot == null) {
            return;
        }
        var project = projectApi.requireProject(snapshot.getProjectId());
        if ("project.cover.generate".equals(snapshot.getActionKey())) {
            projectApi.lockForCoverMutation(snapshot.getProjectId(), project.userId());
        } else {
            projectApi.lockForGeneratedResource(snapshot.getProjectId(), project.userId());
        }
        var currentProject = projectApi.requireProject(snapshot.getProjectId());
        if (com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectLifecycle.ARCHIVED.equals(
                currentProject.lifecycleStage())) {
            return;
        }
        var run = runRepository.findLockedById(snapshot.getId()).orElse(null);
        if (run == null
                || !com.xuejiai.aaf.module.ai.aigc.execution.api.AigcExecutionRunStatus.RUNNING
                        .equals(run.getStatus())) {
            return;
        }
        if ("project.cover.generate".equals(run.getActionKey())) {
            handleCoverTerminal(run, event);
            return;
        }
        if ("SUCCESS".equals(event.status()) && event.outputMediaVersionId() != null) {
            mediaApi.getByVersionId(event.outputMediaVersionId(), currentProject.userId());
            run.setOutputPayload(
                    Map.of(
                            "taskIds", List.of(event.taskId()),
                            "mediaVersionIds", List.of(event.outputMediaVersionId())));
            run.setVersion(run.getVersion() + 1);
            runRepository.save(run);
            eventPublisher.publishEvent(
                    new AigcExecutionCandidateProducedEvent(
                            event.eventId(),
                            run.getId(),
                            run.getExecutionSubmissionId(),
                            run.getExecutionReservationId(),
                            run.getRootExecutionRunId(),
                            run.getProjectId(),
                            run.getObjectId(),
                            List.of(),
                            List.of(event.outputMediaVersionId()),
                            Instant.now()));
            return;
        }
        failRun(run, event.failureCode());
    }

    private void handleCoverTerminal(AigcExecutionRun run, AigcTaskTerminalEvent event) {
        if ("SUCCESS".equals(event.status()) && event.outputMediaVersionId() != null) {
            var applied =
                    projectApi.applyGeneratedCover(
                            run.getProjectId(),
                            event.outputMediaVersionId(),
                            longValue(run.getEffectiveInput(), "expectedCoverMediaVersionId"),
                            run.getId());
            run.setOutputPayload(
                    Map.of(
                            "taskIds", List.of(event.taskId()),
                            "mediaVersionIds", List.of(event.outputMediaVersionId()),
                            "coverApplied", applied));
            run.setStatus(
                    com.xuejiai.aaf.module.ai.aigc.execution.api.AigcExecutionRunStatus.SUCCEEDED);
            run.setEndTime(LocalDateTime.now());
            run.setVersion(run.getVersion() + 1);
            runRepository.save(run);
            terminalService.onRunTerminal(run);
            return;
        }
        projectApi.markCoverExecutionTerminal(
                run.getProjectId(),
                run.getId(),
                com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectCoverStatus.FAILED);
        failRun(run, event.failureCode());
    }

    private Long longValue(Map<String, Object> payload, String field) {
        if (payload == null || !(payload.get(field) instanceof Number value)) {
            return null;
        }
        return value.longValue();
    }

    private void failRun(AigcExecutionRun run, String failureCode) {
        run.setStatus(com.xuejiai.aaf.module.ai.aigc.execution.api.AigcExecutionRunStatus.FAILED);
        run.setErrorMessage(failureCode);
        run.setEndTime(LocalDateTime.now());
        run.setVersion(run.getVersion() + 1);
        runRepository.save(run);
        terminalService.onRunTerminal(run);
    }
}
