package com.xuejiai.aaf.framework.intelligent.infrastructure.agent.persistence;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.xuejiai.aaf.framework.engine.skill.SkillStore;
import com.xuejiai.aaf.framework.engine.skill.SkillStore.SkillRecord;
import com.xuejiai.aaf.framework.intelligent.agent.port.SkillCatalogPort;
import com.xuejiai.aaf.framework.intelligent.core.skill.SkillDef;
import com.xuejiai.aaf.framework.intelligent.core.skill.SkillVersionRef;

/** 基于 AAF 版本化 SkillStore 的只读技能目录适配器。 */
public final class JpaSkillCatalogAdapter implements SkillCatalogPort {

    private final SkillStore skillStore;

    public JpaSkillCatalogAdapter(SkillStore skillStore) {
        this.skillStore = Objects.requireNonNull(skillStore, "skillStore 不能为空");
    }

    @Override
    public Optional<SkillDef> findByCode(String skillCode) {
        Objects.requireNonNull(skillCode, "skillCode 不能为空");
        if (skillCode.isBlank()) {
            throw new IllegalArgumentException("skillCode 不能为空白");
        }
        return skillStore.findByCode(skillCode).map(this::toDomain);
    }

    @Override
    public List<SkillDef> findBuiltIn() {
        return skillStore.findBuiltIn().stream().map(this::toDomain).toList();
    }

    private SkillDef toDomain(SkillRecord record) {
        return new SkillDef(
                record.skillId(),
                record.code(),
                record.name(),
                record.summary(),
                new SkillVersionRef(record.skillId(), record.versionId(), record.version()),
                record.content(),
                record.requiredToolNames(),
                record.requiredModelCapabilities(),
                record.builtIn());
    }
}
