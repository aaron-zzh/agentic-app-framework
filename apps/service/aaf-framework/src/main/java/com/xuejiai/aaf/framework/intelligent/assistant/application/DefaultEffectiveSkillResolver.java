package com.xuejiai.aaf.framework.intelligent.assistant.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.xuejiai.aaf.framework.intelligent.agent.port.SkillCatalogPort;
import com.xuejiai.aaf.framework.intelligent.assistant.model.Role;
import com.xuejiai.aaf.framework.intelligent.core.skill.SkillDef;

/** {@link EffectiveSkillResolver} 的默认实现。 */
public final class DefaultEffectiveSkillResolver implements EffectiveSkillResolver {

    private final SkillCatalogPort skillCatalog;

    public DefaultEffectiveSkillResolver(SkillCatalogPort skillCatalog) {
        this.skillCatalog = Objects.requireNonNull(skillCatalog, "skillCatalog 不能为空");
    }

    @Override
    public List<SkillDef> resolve(List<Role> assignedRoles) {
        Objects.requireNonNull(assignedRoles, "assignedRoles 不能为空");
        var merged = new LinkedHashMap<Long, SkillDef>();

        skillCatalog.findBuiltIn().forEach(skill -> merged.put(skill.skillId(), skill));
        assignedRoles.stream()
                .flatMap(role -> role.skillKeys().stream())
                .map(skillCatalog::findByCode)
                .flatMap(Optional::stream)
                .forEach(skill -> merged.put(skill.skillId(), skill));

        return List.copyOf(merged.values());
    }
}
