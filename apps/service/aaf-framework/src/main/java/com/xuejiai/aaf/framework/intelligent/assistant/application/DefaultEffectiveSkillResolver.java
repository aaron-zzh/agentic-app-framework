package com.xuejiai.aaf.framework.intelligent.assistant.application;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.xuejiai.aaf.framework.intelligent.agent.port.SkillCatalogPort;
import com.xuejiai.aaf.framework.intelligent.assistant.model.Role;
import com.xuejiai.aaf.framework.intelligent.core.skill.SkillDef;

/** Role 边界内的 immutable APPROVED Skill 解析器。 */
public final class DefaultEffectiveSkillResolver implements EffectiveSkillResolver {

    private final SkillCatalogPort skillCatalog;

    public DefaultEffectiveSkillResolver(SkillCatalogPort skillCatalog) {
        this.skillCatalog = Objects.requireNonNull(skillCatalog, "skillCatalog 不能为空");
    }

    @Override
    public List<SkillDef> resolve(Role effectiveRole, Set<String> skillKeys) {
        Objects.requireNonNull(effectiveRole, "effectiveRole 不能为空");
        var requested = Set.copyOf(Objects.requireNonNull(skillKeys, "skillKeys 不能为空"));
        if (requested.isEmpty()) {
            return List.of();
        }
        if (!effectiveRole.skillKeys().containsAll(requested)) {
            throw new IllegalArgumentException("Skill 不属于当前有效 Role");
        }
        return requested.stream()
                .map(
                        skillKey ->
                                skillCatalog
                                        .findByCode(skillKey)
                                        .orElseThrow(
                                                () ->
                                                        new IllegalArgumentException(
                                                                "不存在 APPROVED Skill: " + skillKey)))
                .toList();
    }
}
