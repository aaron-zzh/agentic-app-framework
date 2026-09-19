package com.xuejiai.aaf.module.ai.aigc.project.api;

import java.util.List;

/** Execution/Task/Credit 向项目只读投影提供的聚合证据。 */
public interface AigcProjectProgressEvidencePort {

    Evidence load(Long projectId, Long userId);

    record Evidence(
            List<ExecutionProgressRef> executions,
            ActualCreditSummary credits,
            java.util.Map<Long, ActualCreditSummary> creditsByObject) {
        public Evidence {
            executions = executions == null ? List.of() : List.copyOf(executions);
            creditsByObject =
                    creditsByObject == null
                            ? java.util.Map.of()
                            : java.util.Map.copyOf(creditsByObject);
        }
    }

    record ExecutionProgressRef(
            Long executionRunId,
            Long projectObjectId,
            String actionKey,
            String targetType,
            String status,
            List<TaskProgressRef> tasks,
            Long estimatedCredits,
            Long reservedCredits,
            String failureReason) {
        public ExecutionProgressRef {
            tasks = tasks == null ? List.of() : List.copyOf(tasks);
        }
    }

    record TaskProgressRef(
            Long taskId,
            Long executionRunId,
            String taskType,
            String status,
            Long mediaVersionId,
            Long creditTxId,
            String failureReason) {}

    record ActualCreditSummary(
            long estimated,
            long reserved,
            long settled,
            long refunded,
            long netActual,
            Long unknown,
            List<Long> unknownTaskIds) {
        public ActualCreditSummary {
            unknownTaskIds = unknownTaskIds == null ? List.of() : List.copyOf(unknownTaskIds);
        }
    }
}
