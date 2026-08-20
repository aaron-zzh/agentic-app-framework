package com.xuejiai.aaf.framework.intelligent.assistant.model;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ControlMode;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.AssistantId;

/** Assistant 当前定义聚合；运行时只读取当前行，不提供版本选择。 */
public record AssistantDefinition(
        AssistantId assistantId,
        String systemKey,
        String sourceSystemKey,
        TemplateOwnership ownership,
        AssistantVersion version,
        String maintainer,
        Actor actor,
        List<Role> roles,
        List<SkillBinding> assistantSkillBindings,
        Set<String> assistantToolKeys,
        String defaultRoleKey,
        MemoryStrategy memoryStrategy,
        String modelId,
        ToolPolicy toolPolicy,
        Set<ControlMode> supportedControlModes,
        RiskPolicy defaultRiskPolicy,
        Lifecycle lifecycle) {

    public AssistantDefinition {
        Objects.requireNonNull(assistantId, "assistantId 不能为空");
        Objects.requireNonNull(ownership, "ownership 不能为空");
        Objects.requireNonNull(version, "version 不能为空");
        maintainer = requireText(maintainer, "maintainer");
        Objects.requireNonNull(actor, "actor 不能为空");
        roles = List.copyOf(Objects.requireNonNull(roles, "roles 不能为空"));
        assistantSkillBindings =
                SkillBinding.copyOf(assistantSkillBindings, "assistantSkillBindings");
        assistantToolKeys = copyTextSet(assistantToolKeys, "assistantToolKeys");
        defaultRoleKey = requireText(defaultRoleKey, "defaultRoleKey");
        Objects.requireNonNull(memoryStrategy, "memoryStrategy 不能为空");
        modelId = modelId == null || modelId.isBlank() ? null : modelId.trim();
        Objects.requireNonNull(toolPolicy, "toolPolicy 不能为空");
        supportedControlModes =
                Set.copyOf(
                        Objects.requireNonNull(
                                supportedControlModes, "supportedControlModes 不能为空"));
        Objects.requireNonNull(defaultRiskPolicy, "defaultRiskPolicy 不能为空");
        Objects.requireNonNull(lifecycle, "lifecycle 不能为空");
        validateIdentity(ownership, systemKey, sourceSystemKey);
        validateRoles(roles, defaultRoleKey);
        validateSkillOwnership(roles, SkillBinding.skillKeys(assistantSkillBindings));
        validateToolPolicy(roles, assistantToolKeys, toolPolicy);
        validateModes(supportedControlModes);
    }

    public AssistantCapabilityManifest capabilityManifest() {
        return AssistantCapabilityManifest.from(this);
    }

    public Role defaultRole() {
        return requireRole(defaultRoleKey);
    }

    public Role requireRole(String roleKey) {
        return roles.stream()
                .filter(role -> role.key().equals(roleKey))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Assistant 未配置 Role: " + roleKey));
    }

    public Set<String> assistantSkillKeys() {
        return SkillBinding.skillKeys(assistantSkillBindings);
    }

    public Set<String> assistantAlwaysSkillKeys() {
        return SkillBinding.skillKeys(assistantSkillBindings, SkillActivationMode.ALWAYS);
    }

    public Set<String> assistantOnDemandSkillKeys() {
        return SkillBinding.skillKeys(assistantSkillBindings, SkillActivationMode.ON_DEMAND);
    }

    public Set<String> candidateSkillKeys(Role role) {
        var configuredRole = requireConfiguredRole(role);
        var candidates = new LinkedHashSet<String>();
        assistantSkillKeys().forEach(candidates::add);
        configuredRole.skillKeys().forEach(candidates::add);
        return Collections.unmodifiableSet(candidates);
    }

    public Set<String> candidateOnDemandSkillKeys(Role role) {
        var configuredRole = requireConfiguredRole(role);
        var candidates = new LinkedHashSet<String>();
        assistantOnDemandSkillKeys().forEach(candidates::add);
        configuredRole.onDemandSkillKeys().forEach(candidates::add);
        return Collections.unmodifiableSet(candidates);
    }

    public Set<String> candidateToolKeys(Role role) {
        var configuredRole = requireConfiguredRole(role);
        var candidates = new LinkedHashSet<String>();
        configuredRole.toolKeys().stream().sorted().forEach(candidates::add);
        assistantToolKeys.stream().sorted().forEach(candidates::add);
        return Collections.unmodifiableSet(candidates);
    }

    public boolean isRoleSkill(Role role, String skillKey) {
        return requireConfiguredRole(role).skillKeys().contains(requireText(skillKey, "skillKey"));
    }

    public boolean isAssistantSkill(String skillKey) {
        return assistantSkillKeys().contains(requireText(skillKey, "skillKey"));
    }

    private Role requireConfiguredRole(Role role) {
        Objects.requireNonNull(role, "role 不能为空");
        var configured = requireRole(role.key());
        if (!configured.equals(role)) {
            throw new IllegalArgumentException("Role 内容与 Assistant 当前定义不一致: " + role.key());
        }
        return configured;
    }

    public void requireControlMode(ControlMode mode) {
        if (!supportedControlModes.contains(mode)) {
            throw new IllegalStateException("Assistant 不支持控制模式: " + mode);
        }
    }

    private static void validateIdentity(
            TemplateOwnership ownership, String systemKey, String sourceSystemKey) {
        switch (ownership) {
            case SYSTEM_MANAGED -> {
                requireText(systemKey, "systemKey");
                if (sourceSystemKey != null) {
                    throw new IllegalArgumentException("系统模板不能声明 sourceSystemKey");
                }
            }
            case USER_COPY -> {
                if (systemKey != null) {
                    throw new IllegalArgumentException("用户副本不能保留受管 systemKey");
                }
                requireText(sourceSystemKey, "sourceSystemKey");
            }
            case USER_OWNED -> {
                if (systemKey != null || sourceSystemKey != null) {
                    throw new IllegalArgumentException("用户自建定义不能声明系统模板标识");
                }
            }
        }
    }

    private static void validateRoles(List<Role> roles, String defaultRoleKey) {
        if (roles.isEmpty()) {
            throw new IllegalArgumentException("roles 不能为空");
        }
        var roleKeys = roles.stream().map(Role::key).toList();
        if (roleKeys.size() != Set.copyOf(roleKeys).size()) {
            throw new IllegalArgumentException("roles 不能包含重复 key");
        }
        if (!roleKeys.contains(defaultRoleKey)) {
            throw new IllegalArgumentException(
                    "defaultRoleKey 必须引用 ai_assistant_role.is_default 关联的 Role");
        }
    }

    private static void validateSkillOwnership(List<Role> roles, Set<String> assistantSkillKeys) {
        var roleSkillKeys =
                roles.stream()
                        .flatMap(role -> role.skillKeys().stream())
                        .collect(java.util.stream.Collectors.toUnmodifiableSet());
        var duplicates =
                assistantSkillKeys.stream().filter(roleSkillKeys::contains).sorted().toList();
        if (!duplicates.isEmpty()) {
            throw new IllegalArgumentException("Assistant Skill 不能同时属于任一 Role: " + duplicates);
        }
    }

    private static void validateToolPolicy(
            List<Role> roles, Set<String> assistantToolKeys, ToolPolicy toolPolicy) {
        var allowedToolKeys = new LinkedHashSet<String>();
        roles.stream().flatMap(role -> role.toolKeys().stream()).forEach(allowedToolKeys::add);
        allowedToolKeys.addAll(assistantToolKeys);
        if (!allowedToolKeys.equals(toolPolicy.rules().keySet())) {
            throw new IllegalArgumentException(
                    "所有 Role.toolKeys 与 Assistant.toolKeys 的并集必须与 ToolPolicy 完全一致");
        }
    }

    private static Set<String> copyTextSet(Set<String> values, String name) {
        Objects.requireNonNull(values, name + " 不能为空");
        var copy = new LinkedHashSet<String>();
        values.stream().map(value -> requireText(value, name)).sorted().forEach(copy::add);
        return Collections.unmodifiableSet(copy);
    }

    private static void validateModes(Set<ControlMode> modes) {
        if (modes.isEmpty()) {
            throw new IllegalArgumentException("supportedControlModes 不能为空");
        }
        if (modes.stream()
                .anyMatch(
                        mode ->
                                mode != ControlMode.READ_ONLY
                                        && mode != ControlMode.COLLABORATIVE
                                        && mode != ControlMode.DELEGATED)) {
            throw new IllegalArgumentException("仅支持 READ_ONLY、COLLABORATIVE 和 DELEGATED");
        }
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name + " 不能为空");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " 不能为空白");
        }
        return value;
    }

    public enum TemplateOwnership {
        SYSTEM_MANAGED,
        USER_OWNED,
        USER_COPY
    }

    public enum RiskPolicy {
        READ_ONLY,
        CONFIRM_WRITES,
        HANDOFF_ONLY
    }

    public enum Lifecycle {
        DRAFT,
        PUBLISHED,
        DEPRECATED,
        DISABLED
    }
}
