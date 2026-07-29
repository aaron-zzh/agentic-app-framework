package com.xuejiai.aaf.framework.intelligent.assistant.model;

import java.util.List;
import java.util.Set;

import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ControlMode;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.AssistantId;

/** AssistantDefinition 的面向用户只读投影。 */
public record AssistantCapabilityManifest(
        AssistantId assistantId,
        String systemKey,
        AssistantVersion version,
        String maintainer,
        String name,
        List<String> responsibilities,
        List<String> nonResponsibilities,
        Set<ControlMode> supportedControlModes,
        Set<String> businessActions,
        Set<String> memoryScopes,
        AssistantDefinition.RiskPolicy defaultRiskPolicy,
        AssistantDefinition.Lifecycle lifecycle) {

    public static AssistantCapabilityManifest from(AssistantDefinition definition) {
        var actions =
                definition.skillRoutes().stream()
                        .map(SkillRoute::actionKey)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return new AssistantCapabilityManifest(
                definition.assistantId(),
                definition.systemKey(),
                definition.version(),
                definition.maintainer(),
                definition.actor().name(),
                definition.role().responsibilities(),
                definition.role().nonResponsibilities(),
                definition.supportedControlModes(),
                actions,
                definition.memoryStrategy().recallScopes(),
                definition.defaultRiskPolicy(),
                definition.lifecycle());
    }
}
