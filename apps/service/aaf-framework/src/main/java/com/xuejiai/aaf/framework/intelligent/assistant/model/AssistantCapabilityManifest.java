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
        String defaultRoleKey,
        String modelId,
        Set<String> roleKeys,
        List<String> responsibilities,
        List<String> nonResponsibilities,
        Set<ControlMode> supportedControlModes,
        Set<String> skillKeys,
        Set<String> memoryScopes,
        AssistantDefinition.RiskPolicy defaultRiskPolicy,
        AssistantDefinition.Lifecycle lifecycle) {

    public static AssistantCapabilityManifest from(AssistantDefinition definition) {
        var roleKeys =
                definition.roles().stream()
                        .map(Role::key)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet());
        var skillKeys =
                definition.roles().stream()
                        .flatMap(role -> role.skillKeys().stream())
                        .collect(java.util.stream.Collectors.toUnmodifiableSet());
        var responsibilities =
                definition.roles().stream()
                        .flatMap(role -> role.responsibilities().stream())
                        .distinct()
                        .toList();
        var nonResponsibilities =
                definition.roles().stream()
                        .flatMap(role -> role.nonResponsibilities().stream())
                        .distinct()
                        .toList();
        return new AssistantCapabilityManifest(
                definition.assistantId(),
                definition.systemKey(),
                definition.version(),
                definition.maintainer(),
                definition.actor().name(),
                definition.defaultRoleKey(),
                definition.modelId(),
                roleKeys,
                responsibilities,
                nonResponsibilities,
                definition.supportedControlModes(),
                skillKeys,
                definition.memoryStrategy().recallScopes(),
                definition.defaultRiskPolicy(),
                definition.lifecycle());
    }
}
