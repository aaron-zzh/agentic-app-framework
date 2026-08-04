package com.xuejiai.aaf.module.ai.aigc.execution.event;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.xuejiai.aaf.module.ai.aigc.execution.repository.AigcExecutionRunRepository;
import com.xuejiai.aaf.module.ai.aigc.execution.repository.AigcExecutionTaskRefRepository;
import com.xuejiai.aaf.module.ai.aigc.media.api.AigcMediaApi;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectApi;
import com.xuejiai.aaf.module.ai.aigc.project.event.AigcExecutionCandidateProducedEvent;
import com.xuejiai.aaf.module.ai.aigc.task.event.AigcTaskTerminalEvent;

import lombok.RequiredArgsConstructor;

/** 汇总媒体子任务终态；成功时只发布候选，不直接写 Project 仓储。 */
@Component
@RequiredArgsConstructor
public class AigcTaskTerminalListener {

    private final AigcExecutionTaskRefRepository taskRefRepository;
    private final AigcExecutionRunRepository runRepository;
    private final AigcProjectApi projectApi;
    private final AigcMediaApi mediaApi;
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
        var run = runRepository.findById(reference.getExecutionRunId()).orElse(null);
        if (run == null || !"running".equals(run.getStatus())) {
            return;
        }
        if ("SUCCESS".equals(event.status()) && event.outputMediaVersionId() != null) {
            var project = projectApi.requireProject(run.getProjectId());
            mediaApi.getByVersionId(event.outputMediaVersionId(), project.userId());
            run.setOutputPayload(
                    java.util.Map.of(
                            "taskIds", List.of(event.taskId()),
                            "mediaVersionIds", List.of(event.outputMediaVersionId())));
            run.setVersion(run.getVersion() + 1);
            runRepository.save(run);
            eventPublisher.publishEvent(
                    new AigcExecutionCandidateProducedEvent(
                            event.eventId(),
                            run.getId(),
                            run.getProjectId(),
                            run.getObjectId(),
                            List.of(),
                            List.of(event.outputMediaVersionId()),
                            Instant.now()));
            return;
        }
        run.setStatus("failed");
        run.setErrorMessage(event.failureCode());
        run.setEndTime(LocalDateTime.now());
        run.setVersion(run.getVersion() + 1);
        runRepository.save(run);
    }
}
