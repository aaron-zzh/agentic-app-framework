package com.xuejiai.aaf.module.ai.aigc.execution.event;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.xuejiai.aaf.module.ai.aigc.execution.api.AigcActionCommand;
import com.xuejiai.aaf.module.ai.aigc.execution.api.AigcExecutionApi;
import com.xuejiai.aaf.module.ai.aigc.execution.api.AigcExecutionRunStatus;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectApi;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectCoverStatus;
import com.xuejiai.aaf.module.ai.aigc.project.event.AigcProjectCoverGenerationRequestedEvent;
import com.xuejiai.aaf.module.ai.aigc.project.event.AigcProjectCoverSupersededEvent;

import lombok.RequiredArgsConstructor;

/** 将 Project 封面意图正向投递到 Execution。 */
@Component
@RequiredArgsConstructor
public class AigcProjectCoverExecutionListener {

    private final AigcExecutionApi executionApi;
    private final AigcProjectApi projectApi;

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT,
            fallbackExecution = true)
    public void onGenerationRequested(AigcProjectCoverGenerationRequestedEvent event) {
        try {
            var run =
                    executionApi.submitDeferred(
                            new AigcActionCommand(
                                    event.projectId(),
                                    null,
                                    "project.cover.generate",
                                    event.prompt(),
                                    null,
                                    Map.of(),
                                    List.of(),
                                    List.of(),
                                    event.expectedGraphRevision(),
                                    true,
                                    event.idempotencyKey()));
            if (run.status().isActive()) {
                projectApi.markCoverExecutionStarted(event.projectId(), run.id());
            } else {
                projectApi.markCoverExecutionStarted(event.projectId(), run.id());
                var status =
                        run.status() == AigcExecutionRunStatus.SUCCEEDED
                                ? AigcProjectCoverStatus.READY
                                : AigcProjectCoverStatus.FAILED;
                projectApi.markCoverExecutionTerminal(event.projectId(), run.id(), status);
            }
        } catch (RuntimeException error) {
            projectApi.markCoverGenerationFailed(event.projectId());
            throw error;
        }
    }

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT,
            fallbackExecution = true)
    public void onSuperseded(AigcProjectCoverSupersededEvent event) {
        executionApi.cancelProjectCoverRuns(event.projectId(), event.reason());
    }
}
