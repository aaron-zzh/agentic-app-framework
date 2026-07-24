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
        Role role,
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
        Objects.requireNonNull(role, "role 不能为空");
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
        validateRoutes(role, skillRoutes);
        validateToolPolicy(role, toolPolicy);
        validateModes(supportedControlModes);
    }

    /** 能力清单由定义实时投影，不形成第二份配置源。 */
    public AssistantCapabilityManifest capabilityManifest() {
        return AssistantCapabilityManifest.from(this);
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

    private static void validateRoutes(Role role, List<SkillRoute> routes) {
        if (routes.isEmpty()) {
            throw new IllegalArgumentException("skillRoutes 不能为空");
        }
        var keys = routes.stream().map(SkillRoute::skillKey).toList();
        if (keys.size() != Set.copyOf(keys).size()) {
            throw new IllegalArgumentException("skillRoutes 不能包含重复 skillKey");
        }
        if (!role.skillKeys().containsAll(keys)) {
            throw new IllegalArgumentException("SkillRoute 必须属于 Role.skillKeys");
        }
        if (routes.stream().filter(SkillRoute::defaultRoute).count() != 1) {
            throw new IllegalArgumentException("Assistant 必须且只能有一个默认 SkillRoute");
        }
    }

    private static void validateToolPolicy(Role role, ToolPolicy toolPolicy) {
        if (!role.toolKeys().equals(toolPolicy.rules().keySet())) {
            throw new IllegalArgumentException("Role.toolKeys 必须与 ToolPolicy 完全一致");
        }
    }

    private static void validateModes(Set<ControlMode> modes) {
        if (modes.isEmpty()) {
            throw new IllegalArgumentException("supportedControlModes 不能为空");
        }
        if (modes.stream()
                .anyMatch(mode -> mode != ControlMode.READ_ONLY && mode != ControlMode.COLLABORATIVE)) {
            throw new IllegalArgumentException("P2 仅支持 READ_ONLY 和 COLLABORATIVE");
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
