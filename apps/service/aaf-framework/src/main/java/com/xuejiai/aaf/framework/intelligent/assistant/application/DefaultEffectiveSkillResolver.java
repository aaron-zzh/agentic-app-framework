package com.xuejiai.aaf.framework.intelligent.assistant.application;

import java.util.List;
import java.util.Objects;

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
    public List<SkillDef> resolve(Role effectiveRole, String skillKey) {
        Objects.requireNonNull(effectiveRole, "effectiveRole 不能为空");
        Objects.requireNonNull(skillKey, "skillKey 不能为空");
        if (!effectiveRole.skillKeys().contains(skillKey)) {
            throw new IllegalArgumentException("Skill 不属于当前有效 Role: " + skillKey);
        }
        return skillCatalog.findByCode(skillKey).stream().toList();
    }
}
