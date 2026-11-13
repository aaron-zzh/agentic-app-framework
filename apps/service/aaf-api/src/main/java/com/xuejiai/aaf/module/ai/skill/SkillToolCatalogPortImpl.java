package com.xuejiai.aaf.module.ai.skill;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.engine.skill.SkillToolRequirement;
import com.xuejiai.aaf.framework.intelligent.agent.port.SkillToolCatalogPort;

import lombok.RequiredArgsConstructor;

/**
 * SkillToolCatalogPort 的 JPA 桥接；数据来自 ai_skill_tool_requirement（内置 Skill 由 md 同步写入，用户 Skill 由接口写入）。
 */
@Component
@RequiredArgsConstructor
public class SkillToolCatalogPortImpl implements SkillToolCatalogPort {

    private final SkillToolRequirementRepository repository;

    @Override
    public Map<Long, List<SkillToolDeclaration>> findByVersionIds(Set<Long> skillVersionIds) {
        if (skillVersionIds.isEmpty()) {
            return Map.of();
        }
        var grouped =
                repository
                        .findBySkillVersionIdInOrderBySkillVersionIdAscSortOrderAscIdAsc(
                                skillVersionIds)
                        .stream()
                        .collect(
                                Collectors.groupingBy(
                                        SkillToolRequirement::getSkillVersionId,
                                        LinkedHashMap::new,
                                        Collectors.mapping(
                                                SkillToolCatalogPortImpl::toDomain,
                                                Collectors.toUnmodifiableList())));
        return Map.copyOf(grouped);
    }

    private static SkillToolDeclaration toDomain(SkillToolRequirement requirement) {
        return new SkillToolDeclaration(
                requirement.getToolName(), Boolean.TRUE.equals(requirement.getRequired()));
    }
}
