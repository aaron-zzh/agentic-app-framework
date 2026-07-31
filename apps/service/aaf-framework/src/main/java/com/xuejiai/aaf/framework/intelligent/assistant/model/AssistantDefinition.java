package com.xuejiai.aaf.framework.intelligent.assistant.model;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ControlMode;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.AssistantId;

/** Assistant 的版本化纯领域定义，是内置、自建和复制助理的统一配置源。 */
public record AssistantDefinition(
        AssistantId assistantId,
        String systemKey,
        String sourceSystemKey,
        TemplateOwnership ownership,
        AssistantVersion version,
        String maintainer,
        Actor actor,
        List<Role> roles,
        String defaultRoleKey,
        MemoryStrategy memoryStrategy,
        List<SkillRoute> skillRoutes,
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
        defaultRoleKey = requireText(defaultRoleKey, "defaultRoleKey");
        Objects.requireNonNull(memoryStrategy, "memoryStrategy 不能为空");
        skillRoutes = List.copyOf(Objects.requireNonNull(skillRoutes, "skillRoutes 不能为空"));
        Objects.requireNonNull(toolPolicy, "toolPolicy 不能为空");
        supportedControlModes =
                Set.copyOf(
                        Objects.requireNonNull(
                                supportedControlModes, "supportedControlModes 不能为空"));
        Objects.requireNonNull(defaultRiskPolicy, "defaultRiskPolicy 不能为空");
        Objects.requireNonNull(lifecycle, "lifecycle 不能为空");
        validateIdentity(ownership, systemKey, sourceSystemKey);
        validateRoles(roles, defaultRoleKey);
        validateRoutes(roles, defaultRoleKey, skillRoutes);
        validateToolPolicy(roles, toolPolicy);
        validateModes(supportedControlModes);
    }

    /** 能力清单由定义实时投影，不形成第二份配置源。 */
    public AssistantCapabilityManifest capabilityManifest() {
        return AssistantCapabilityManifest.from(this);
    }

    public Role defaultRole() {
        return requireRole(defaultRoleKey);
    }

    public Role roleFor(SkillRoute route) {
        Objects.requireNonNull(route, "route 不能为空");
        return requireRole(route.roleKey());
    }

    public Role requireRole(String roleKey) {
        return roles.stream()
                .filter(role -> role.key().equals(roleKey))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Assistant 未配置 Role: " + roleKey));
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
            throw new IllegalArgumentException("defaultRoleKey 必须引用已配置 Role");
        }
    }

    private static void validateRoutes(
            List<Role> roles, String defaultRoleKey, List<SkillRoute> routes) {
        if (routes.isEmpty()) {
            throw new IllegalArgumentException("skillRoutes 不能为空");
        }
        var keys = routes.stream().map(SkillRoute::skillKey).toList();
        if (keys.size() != Set.copyOf(keys).size()) {
            throw new IllegalArgumentException("skillRoutes 不能包含重复 skillKey");
        }
        for (var route : routes) {
            var role =
                    roles.stream()
                            .filter(candidate -> candidate.key().equals(route.roleKey()))
                            .findFirst()
                            .orElseThrow(
                                    () ->
                                            new IllegalArgumentException(
                                                    "SkillRoute 引用了未配置 Role: "
                                                            + route.roleKey()));
            if (!role.skillKeys().contains(route.skillKey())) {
                throw new IllegalArgumentException(
                        "SkillRoute 必须属于对应 Role.skillKeys: " + route.skillKey());
            }
        }
        var defaultRoutes = routes.stream().filter(SkillRoute::defaultRoute).toList();
        if (defaultRoutes.size() != 1) {
            throw new IllegalArgumentException("Assistant 必须且只能有一个默认 SkillRoute");
        }
        if (!defaultRoutes.getFirst().roleKey().equals(defaultRoleKey)) {
            throw new IllegalArgumentException("默认 SkillRoute 必须属于默认 Role");
        }
    }

    private static void validateToolPolicy(List<Role> roles, ToolPolicy toolPolicy) {
        var roleToolKeys =
                roles.stream()
                        .flatMap(role -> role.toolKeys().stream())
                        .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (!roleToolKeys.equals(toolPolicy.rules().keySet())) {
            throw new IllegalArgumentException("所有 Role.toolKeys 的并集必须与 ToolPolicy 完全一致");
        }
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
