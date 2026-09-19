package com.xuejiai.aaf.module.ai.aigc.project.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcExecutionBindingVersionRef;

public record AigcProjectView(
        Long id,
        Long orgId,
        Long workspaceId,
        String name,
        AigcProjectLifecycle lifecycleStage,
        Integer version,
        Long configurationSnapshotId,
        String configurationSnapshotHash,
        String projectTypeCode,
        String productionMode,
        String generationMode,
        List<Long> channelSpecVersionIds,
        List<AigcExecutionBindingVersionRef> executionBindings,
        BigDecimal budgetLimit,
        BigDecimal costUsed,
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
        Long coverMediaVersionId,
        Long userId) {

    public AigcProjectView {
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
    }
}
