package com.xuejiai.aaf.module.ai.aigc.configuration.api;

import java.util.List;

/** 项目配置解析命令，所有版本字段均为已发布记录 ID。 */
public record AigcConfigurationResolveCommand(
        String projectTypeCode,
        Long blueprintVersionId,
        Long domainExtensionVersionId,
        List<Long> channelSpecVersionIds,
        String productionMode,
        String budgetTier,
        String qualityTier,
        List<AigcSlotCountOverride> slotOverrides) {

    public AigcConfigurationResolveCommand {
        channelSpecVersionIds =
                channelSpecVersionIds == null ? List.of() : List.copyOf(channelSpecVersionIds);
        slotOverrides = slotOverrides == null ? List.of() : List.copyOf(slotOverrides);
    }
}
