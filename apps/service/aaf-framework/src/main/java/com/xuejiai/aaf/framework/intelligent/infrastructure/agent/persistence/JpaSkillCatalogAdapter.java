package com.xuejiai.aaf.framework.intelligent.infrastructure.agent.persistence;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.xuejiai.aaf.framework.engine.skill.SkillStore;
import com.xuejiai.aaf.framework.engine.skill.SkillStore.SkillRecord;
import com.xuejiai.aaf.framework.intelligent.agent.port.SkillCatalogPort;
import com.xuejiai.aaf.framework.intelligent.core.skill.SkillDef;

/** 基于既有 SkillStore 的技能目录只读适配器。 */
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
                record.name(),
                record.description(),
                record.agentId(),
                parseTriggerKeywords(record.triggerIntent()),
                record.systemPrompt(),
                record.priority(),
                record.builtIn());
    }

    private List<String> parseTriggerKeywords(String triggerIntent) {
        return triggerIntent == null
                ? List.of()
                : List.of(triggerIntent.replaceAll("[\\[\\]\"]", "").split(","));
    }
}
