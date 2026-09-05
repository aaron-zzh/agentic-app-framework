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
        List<AigcExecutionBindingVersionRef> executionBindings,
        String productionMode,
        String budgetTier,
        String qualityTier,
        List<AigcBlueprintSlotTemplateSpec> slotTemplates,
        List<AigcResolvedObjectSpec> resolvedObjects,
        List<AigcBlueprintRelationSpec> relations,
        List<AigcBlueprintActionSpec> actions,
        List<AigcDeliverableSetSpec> deliverableSets,
        AigcBlueprintProcessPolicy processPolicy,
        String snapshotJson) {

    public AigcResolvedConfiguration {
        channelSpecVersionIds = List.copyOf(channelSpecVersionIds);
        executionBindings = List.copyOf(executionBindings);
        slotTemplates = List.copyOf(slotTemplates);
        resolvedObjects = List.copyOf(resolvedObjects);
        relations = List.copyOf(relations);
        actions = List.copyOf(actions);
        deliverableSets = List.copyOf(deliverableSets);
    }
}
