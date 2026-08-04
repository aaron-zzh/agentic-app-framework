package com.xuejiai.aaf.module.ai.aigc.configuration.api;

/** 蓝图关系规格。 */
public record AigcBlueprintRelationSpec(
        String sourceKey, String targetKey, String relationType, String metadataJson) {}
