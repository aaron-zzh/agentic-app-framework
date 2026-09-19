package com.xuejiai.aaf.module.ai.aigc.project.api;

import java.util.List;
import java.util.Map;

import com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcExecutionBindingVersionRef;
import com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcSlotCountOverride;

/** 项目物化时冻结的项目级输入事实。 */
public record AigcProjectSpecification(
        String projectTypeCode,
        String projectTypeVersion,
        String productionMode,
        List<Long> channelSpecVersionIds,
        List<AigcExecutionBindingVersionRef> executionBindings,
        String description,
        String background,
        String targetAudience,
        List<String> requirements,
        List<String> constraints,
        List<String> keyFacts,
        List<String> keyMessages,
        String styleGuidance,
        List<String> successCriteria,
        Map<String, Object> deliveryRequirements,
        String locale,
        String additionalContext,
        List<AigcSlotCountOverride> slotOverrides) {

    public AigcProjectSpecification {
        channelSpecVersionIds =
                channelSpecVersionIds == null ? List.of() : List.copyOf(channelSpecVersionIds);
        executionBindings = executionBindings == null ? List.of() : List.copyOf(executionBindings);
        requirements = requirements == null ? List.of() : List.copyOf(requirements);
        constraints = constraints == null ? List.of() : List.copyOf(constraints);
        keyFacts = keyFacts == null ? List.of() : List.copyOf(keyFacts);
        keyMessages = keyMessages == null ? List.of() : List.copyOf(keyMessages);
        successCriteria = successCriteria == null ? List.of() : List.copyOf(successCriteria);
        deliveryRequirements =
                deliveryRequirements == null ? Map.of() : Map.copyOf(deliveryRequirements);
        slotOverrides = slotOverrides == null ? List.of() : List.copyOf(slotOverrides);
    }
}
