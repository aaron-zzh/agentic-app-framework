package com.xuejiai.aaf.module.ai.aigc.project.api;

import java.util.List;

public record AigcObjectReadiness(
        Long projectObjectId,
        String actionKey,
        AigcObjectReadinessState state,
        List<Long> dependencyObjectIds,
        List<AigcFrozenInputVersionRef> inputVersions,
        String blockedReason,
        List<String> allowedActions) {

    public AigcObjectReadiness {
        dependencyObjectIds =
                dependencyObjectIds == null ? List.of() : List.copyOf(dependencyObjectIds);
        inputVersions = inputVersions == null ? List.of() : List.copyOf(inputVersions);
        allowedActions = allowedActions == null ? List.of() : List.copyOf(allowedActions);
    }
}
