package com.xuejiai.aaf.module.ai.aigc.configuration.api;

import java.util.List;

/** 已解析且可固化为项目快照的配置。 */
public record AigcResolvedConfiguration(
        Long packageVersionId,
        String packageVersion,
        Long projectTypeId,
        String projectTypeVersion,
        Long blueprintVersionId,
        String blueprintCode,
        String blueprintVersion,
        Long domainExtensionVersionId,
        String domainExtensionCode,
        String domainExtensionVersion,
        List<Long> channelSpecVersionIds,
        List<Long> executionBindingVersionIds,
        String productionMode,
        String snapshotJson,
        List<AigcBlueprintObjectSpec> objects,
        List<AigcBlueprintRelationSpec> relations) {}
