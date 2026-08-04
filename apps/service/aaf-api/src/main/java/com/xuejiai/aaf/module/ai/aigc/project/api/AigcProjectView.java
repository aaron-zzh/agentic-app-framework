package com.xuejiai.aaf.module.ai.aigc.project.api;

import java.math.BigDecimal;
import java.util.List;

public record AigcProjectView(
        Long id,
        Long orgId,
        Long workspaceId,
        String name,
        String lifecycleStage,
        Integer version,
        Long configurationSnapshotId,
        String projectTypeCode,
        String domainExtensionCode,
        String productionMode,
        String generationMode,
        List<Long> channelSpecVersionIds,
        BigDecimal budgetLimit,
        BigDecimal costUsed,
        String brief,
        Long userId) {

    public AigcProjectView {
        channelSpecVersionIds =
                channelSpecVersionIds == null ? List.of() : List.copyOf(channelSpecVersionIds);
    }
}
