package com.xuejiai.aaf.module.ai.aigc.execution.event;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.xuejiai.aaf.module.ai.aigc.execution.repository.AigcExecutionRunRepository;
import com.xuejiai.aaf.module.ai.aigc.execution.service.AigcExecutionTerminalService;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectApi;
import com.xuejiai.aaf.module.ai.aigc.project.event.AigcProjectCandidateRegisteredEvent;

import lombok.RequiredArgsConstructor;

/** 将 Project 已登记的 ObjectVersion ID 幂等回填到 ExecutionRun。 */
@Component
@RequiredArgsConstructor
public class AigcProjectCandidateRegisteredListener {

    private final AigcExecutionRunRepository runRepository;
    private final AigcProjectApi projectApi;
    private final AigcExecutionTerminalService terminalService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onCandidateRegistered(AigcProjectCandidateRegisteredEvent event) {
        var snapshot = runRepository.findById(event.executionRunId()).orElse(null);
        if (snapshot == null || !event.projectId().equals(snapshot.getProjectId())) {
            return;
        }
        var project = projectApi.requireProject(event.projectId());
        projectApi.lockForGeneratedResource(event.projectId(), project.userId());
        if (com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectLifecycle.ARCHIVED.equals(
                projectApi.requireProject(event.projectId()).lifecycleStage())) {
            return;
        }
        var run = runRepository.findLockedById(event.executionRunId()).orElse(null);
        if (run == null
                || !event.projectId().equals(run.getProjectId())
                || !com.xuejiai.aaf.module.ai.aigc.execution.api.AigcExecutionRunStatus.RUNNING
                        .equals(run.getStatus())) {
            return;
        }
        var output =
                run.getOutputPayload() == null
                        ? new LinkedHashMap<String, Object>()
                        : new LinkedHashMap<>(run.getOutputPayload());
        output.put("objectVersionIds", event.objectVersionIds());
        run.setOutputPayload(output);
        run.setStatus(
                com.xuejiai.aaf.module.ai.aigc.execution.api.AigcExecutionRunStatus.SUCCEEDED);
        run.setEndTime(LocalDateTime.now());
        run.setVersion(run.getVersion() + 1);
        runRepository.save(run);
        terminalService.onRunTerminal(run);
    }
}
