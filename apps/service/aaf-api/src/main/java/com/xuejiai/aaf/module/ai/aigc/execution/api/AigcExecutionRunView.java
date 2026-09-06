package com.xuejiai.aaf.module.ai.aigc.execution.api;

import java.util.List;

public record AigcExecutionRunView(
        Long id,
        Long projectId,
        Long projectObjectId,
        Long parentExecutionRunId,
        Long rootExecutionRunId,
        String runKind,
        String workflowNodeKey,
        Long executionSubmissionId,
        Long executionReservationId,
        Long targetGraphRevision,
        List<Long> frozenProjectObjectIds,
        java.util.Map<String, Object> effectiveInput,
        Long bindingVersionId,
        String actionKey,
        String targetType,
        String targetRef,
        AigcExecutionRunStatus status,
        List<Long> taskIds,
        List<Long> candidateObjectVersionIds,
        List<Long> candidateMediaVersionIds,
        String runtimeTraceId,
        String runtimeRunId,
        String output,
        Long creditCost) {

    public AigcExecutionRunView {
        frozenProjectObjectIds =
                frozenProjectObjectIds == null ? List.of() : List.copyOf(frozenProjectObjectIds);
        effectiveInput =
                effectiveInput == null ? java.util.Map.of() : java.util.Map.copyOf(effectiveInput);
        taskIds = taskIds == null ? List.of() : List.copyOf(taskIds);
        candidateObjectVersionIds =
                candidateObjectVersionIds == null
                        ? List.of()
                        : List.copyOf(candidateObjectVersionIds);
        candidateMediaVersionIds =
                candidateMediaVersionIds == null
                        ? List.of()
                        : List.copyOf(candidateMediaVersionIds);
    }
}
