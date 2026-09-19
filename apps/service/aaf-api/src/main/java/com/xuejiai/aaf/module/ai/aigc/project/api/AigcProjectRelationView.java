package com.xuejiai.aaf.module.ai.aigc.project.api;

public record AigcProjectRelationView(
        Long id,
        Long sourceObjectId,
        Long targetObjectId,
        String relationType,
        String layer,
        String metadataJson,
        String sourceSemanticRole,
        String targetSemanticRole,
        String sourceOutputContract,
        String targetOutputContract) {}
