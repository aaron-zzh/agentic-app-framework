package com.xuejiai.aaf.module.ai.aigc.configuration.api;

/** 蓝图中的动态槽位模板。 */
public record AigcBlueprintSlotTemplateSpec(
        String templateKey,
        String stableKeyPattern,
        String objectType,
        String displayNamePattern,
        String description,
        String parentTemplateKey,
        Integer orderNo,
        Integer defaultCount,
        Integer minCount,
        Integer maxCount,
        boolean userAddable,
        boolean userRemovable,
        String defaultContractRole,
        String adoptionPolicy,
        String activationConditionJson,
        String userInstructionJson,
        String schemaJson) {}
