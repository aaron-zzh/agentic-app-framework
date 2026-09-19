package com.xuejiai.aaf.module.ai.aigc.configuration.api;

import java.util.List;
import java.util.Map;

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
        String semanticRole,
        String outputContract,
        String adoptionPolicy,
        Map<String, Integer> countByProductionMode,
        ActivationCondition activationCondition,
        String userInstructionJson,
        String initialPayloadJson,
        String payloadSchemaJson) {

    public AigcBlueprintSlotTemplateSpec {
        countByProductionMode =
                countByProductionMode == null ? Map.of() : Map.copyOf(countByProductionMode);
        activationCondition =
                activationCondition == null ? ActivationCondition.empty() : activationCondition;
    }

    /** 仅影响槽位物化数量的可预测激活条件。 */
    public record ActivationCondition(List<String> productionModes, List<String> channelsAny) {

        public ActivationCondition {
            productionModes = productionModes == null ? List.of() : List.copyOf(productionModes);
            channelsAny = channelsAny == null ? List.of() : List.copyOf(channelsAny);
        }

        public static ActivationCondition empty() {
            return new ActivationCondition(List.of(), List.of());
        }
    }
}
