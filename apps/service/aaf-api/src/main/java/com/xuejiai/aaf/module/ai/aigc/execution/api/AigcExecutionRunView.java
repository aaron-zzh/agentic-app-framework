package com.xuejiai.aaf.module.ai.aigc.execution.api;

import java.util.List;

public record AigcExecutionRunView(
        Long id,
        Long projectId,
        Long projectObjectId,
        String actionKey,
        String targetType,
        String targetRef,
        String status,
        List<Long> taskIds,
        List<Long> candidateObjectVersionIds,
        List<Long> candidateMediaVersionIds,
        String runtimeTraceId,
        String runtimeRunId,
        String output,
        Long creditCost) {

    public AigcExecutionRunView {
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
