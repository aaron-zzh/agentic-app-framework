package com.xuejiai.aaf.module.ai.aigc.project.api;

import java.math.BigDecimal;
import java.util.List;

public record AigcProjectView(
        Long id,
        Long orgId,
        Long workspaceId,
        String name,
        AigcProjectLifecycle lifecycleStage,
        Integer version,
        Long configurationSnapshotId,
        String projectTypeCode,
        String domainExtensionCode,
        String productionMode,
        String generationMode,
        List<Long> channelSpecVersionIds,
        List<com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcExecutionBindingVersionRef>
                executionBindings,
        BigDecimal budgetLimit,
        BigDecimal costUsed,
        String description,
        String brief,
        Long coverMediaVersionId,
        Long userId) {

    public AigcProjectView {
        channelSpecVersionIds =
                channelSpecVersionIds == null ? List.of() : List.copyOf(channelSpecVersionIds);
        executionBindings =
                executionBindings == null ? List.of() : List.copyOf(executionBindings);
    }
}
