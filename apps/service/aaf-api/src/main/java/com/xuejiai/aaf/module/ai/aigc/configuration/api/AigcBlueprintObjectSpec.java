package com.xuejiai.aaf.module.ai.aigc.configuration.api;

/** 蓝图对象规格。 */
public record AigcBlueprintObjectSpec(
        String stableKey,
        String objectType,
        String parentKey,
        Integer orderNo,
        String schemaJson) {}
