package com.xuejiai.aaf.framework.intelligent.assistant.application;

/** 默认 Role 选择器：显式 Skill 先限定 Role，否则使用 M2M 绑定的唯一默认 Role。 */
public final class DefaultRoleSelector implements RoleSelector {

    @Override
    public RoleSelection select(RoleSelectionRequest request) {
        var preferredSkillKey = request.preferredSkillKey();
        if (preferredSkillKey == null) {
            return new RoleSelection(
                    request.definition().defaultRole(),
                    "DEFAULT_BINDING",
                    "使用 Assistant 默认 Role 绑定");
        }
        var matches =
                request.definition().roles().stream()
                        .filter(role -> role.skillKeys().contains(preferredSkillKey))
                        .toList();
        if (matches.isEmpty()) {
            throw new IllegalArgumentException(
                    "Assistant 当前 Role Scope 不允许请求该 Skill: " + preferredSkillKey);
        }
        if (matches.size() > 1) {
            var defaultRole = request.definition().defaultRole();
            if (defaultRole.skillKeys().contains(preferredSkillKey)) {
                return new RoleSelection(defaultRole, "REQUEST", "显式 Skill 命中默认 Role");
            }
            throw new IllegalStateException("显式 Skill 同时属于多个非默认 Role，无法唯一选择: " + preferredSkillKey);
        }
        return new RoleSelection(matches.getFirst(), "REQUEST", "显式 Skill 唯一命中 Role");
    }
}
