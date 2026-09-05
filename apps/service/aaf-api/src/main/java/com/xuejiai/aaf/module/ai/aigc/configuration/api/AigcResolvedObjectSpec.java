package com.xuejiai.aaf.module.ai.aigc.configuration.api;

/** 动态槽位模板按项目设置解析后的具体项目对象。 */
public record AigcResolvedObjectSpec(
        String stableKey,
        String blueprintTemplateKey,
        Integer instanceNo,
        String objectType,
        String displayName,
        String parentKey,
        Integer orderNo,
        String contractRole,
        String adoptionPolicy,
        String userInstructionJson,
        String schemaJson) {}
