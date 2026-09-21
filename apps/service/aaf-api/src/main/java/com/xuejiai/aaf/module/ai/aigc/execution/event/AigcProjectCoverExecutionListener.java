package com.xuejiai.aaf.module.ai.aigc.execution.event;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.module.ai.aigc.execution.api.AigcActionCommand;
import com.xuejiai.aaf.module.ai.aigc.execution.api.AigcExecutionApi;
import com.xuejiai.aaf.module.ai.aigc.execution.api.AigcExecutionRunStatus;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectApi;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectCoverStatus;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectObjectView;
import com.xuejiai.aaf.module.ai.aigc.project.event.AigcProjectCoverGenerationRequestedEvent;
import com.xuejiai.aaf.module.ai.aigc.project.event.AigcProjectCoverSupersededEvent;

import lombok.RequiredArgsConstructor;

/** 将 Project 封面意图正向投递到 Execution。 */
@Component
@RequiredArgsConstructor
public class AigcProjectCoverExecutionListener {

    private final AigcExecutionApi executionApi;
    private final AigcProjectApi projectApi;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onGenerationRequested(AigcProjectCoverGenerationRequestedEvent event) {
        try {
            var project = projectApi.requireProject(event.projectId());
            var costObject = resolveCoverCostObject(event.projectId());
            var run =
                    executionApi.submitDeferred(
                            new AigcActionCommand(
                                    event.projectId(),
                                    costObject.id(),
                                    "project.cover.generate",
                                    event.prompt(),
                                    null,
                                    Map.of(),
                                    List.of(),
                                    List.of(),
                                    project.version(),
                                    event.expectedGraphRevision(),
                                    true,
                                    event.idempotencyKey(),
                                    null));
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

    private AigcProjectObjectView resolveCoverCostObject(Long projectId) {
        return projectApi.getGraph(projectId).objects().stream()
                .filter(object -> !"EXCLUDED".equals(object.contractRole()))
                .sorted(
                        Comparator.comparingInt(this::coverCostPriority)
                                .thenComparing(
                                        AigcProjectObjectView::stableKey,
                                        Comparator.nullsLast(Comparator.naturalOrder()))
                                .thenComparing(
                                        AigcProjectObjectView::id,
                                        Comparator.nullsLast(Comparator.naturalOrder())))
                .findFirst()
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        GlobalErrorCode.BAD_REQUEST, "项目不存在可归属封面生成成本的对象"));
    }

    private int coverCostPriority(AigcProjectObjectView object) {
        if ("COMPOSITE_MANIFEST".equals(object.outputContract())) {
            return 0;
        }
        if ("DELIVERABLE".equals(object.semanticRole())) {
            return 1;
        }
        if ("REQUIRED".equals(object.contractRole())) {
            return 2;
        }
        return 3;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onSuperseded(AigcProjectCoverSupersededEvent event) {
        executionApi.cancelProjectCoverRuns(event.projectId(), event.reason());
    }
}
