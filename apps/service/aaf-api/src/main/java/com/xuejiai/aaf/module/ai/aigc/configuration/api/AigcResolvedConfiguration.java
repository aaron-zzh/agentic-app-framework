package com.xuejiai.aaf.module.ai.aigc.configuration.api;

import java.util.List;

/** 已解析且可固化为项目快照的配置。 */
public record AigcResolvedConfiguration(
        String projectTypeVersion,
        Long blueprintVersionId,
        String blueprintCode,
        String blueprintVersion,
        List<Long> channelSpecVersionIds,
        List<AigcExecutionBindingVersionRef> executionBindings,
        String productionMode,
        AigcProjectInitializationSpec projectInitialization,
        List<AigcBlueprintSlotTemplateSpec> slotTemplates,
        List<AigcResolvedObjectSpec> resolvedObjects,
        List<AigcBlueprintRelationSpec> relations,
        List<AigcBlueprintActionSpec> actions,
        List<AigcDeliverableSetSpec> deliverableSets,
        AigcBlueprintProcessPolicy processPolicy) {

    public AigcResolvedConfiguration {
        projectInitialization =
                projectInitialization == null
                        ? AigcProjectInitializationSpec.empty()
                        : projectInitialization;
        channelSpecVersionIds = List.copyOf(channelSpecVersionIds);
        executionBindings = List.copyOf(executionBindings);
        slotTemplates = List.copyOf(slotTemplates);
        resolvedObjects = List.copyOf(resolvedObjects);
        relations = List.copyOf(relations);
        actions = List.copyOf(actions);
        deliverableSets = List.copyOf(deliverableSets);
    }
}
