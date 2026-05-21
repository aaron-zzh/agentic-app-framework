package com.xuejiai.aaf.framework.engine.skill;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.intelligent.core.skill.SkillDef;
import com.xuejiai.aaf.framework.intelligent.core.skill.SkillProvider;

import lombok.RequiredArgsConstructor;

/** 技能匹配引擎：实现 SkillProvider 接口。 用户自定义技能优先于内置技能（同名时用户版本覆盖）。 */
@Service
@RequiredArgsConstructor
public class SkillMatchEngine implements SkillProvider {

    private final SkillDefinitionRepository repository;

    @Override
    public Optional<SkillDef> match(String assistantId, String userInput) {
        // 先查用户自定义技能，再查内置技能
        var skills = repository.findByAssistantIdAndStatus(assistantId, "active");
        if (skills.isEmpty()) {
            skills = repository.findByBuiltInTrueAndStatus("active");
        }
        return skills.stream()
                .filter(s -> matchesIntent(s, userInput))
                .max(Comparator.comparingInt(SkillDefinition::getPriority))
                .map(this::toSkillDef);
    }

    @Override
    public List<SkillDef> getDefinitions(String assistantId) {
        var userSkills = repository.findByAssistantIdAndStatus(assistantId, "active");
        var builtinSkills = repository.findByBuiltInTrueAndStatus("active");
        // 用户技能覆盖同名内置技能
        var userSkillIds = userSkills.stream().map(SkillDefinition::getSkillId).toList();
        var merged = new java.util.ArrayList<>(userSkills);
        builtinSkills.stream()
                .filter(s -> !userSkillIds.contains(s.getSkillId()))
                .forEach(merged::add);
        return merged.stream().map(this::toSkillDef).toList();
    }

    private boolean matchesIntent(SkillDefinition skill, String input) {
        if (skill.getTriggerIntent() == null || skill.getTriggerIntent().isBlank()) return false;
        var triggers = skill.getTriggerIntent().replaceAll("[\\[\\]\"]", "").split(",");
        var lower = input.toLowerCase();
        for (var trigger : triggers) {
            if (lower.contains(trigger.trim().toLowerCase())) return true;
        }
        return false;
    }

    private SkillDef toSkillDef(SkillDefinition entity) {
        var tools =
                entity.getTools() != null
                        ? List.of(entity.getTools().replaceAll("[\\[\\]\"]", "").split(","))
                        : List.<String>of();
        var keywords =
                entity.getTriggerIntent() != null
                        ? List.of(entity.getTriggerIntent().replaceAll("[\\[\\]\"]", "").split(","))
                        : List.<String>of();
        return new SkillDef(
                entity.getSkillId(),
                entity.getName(),
                entity.getDescription(),
                entity.getAgentId(),
                keywords,
                entity.getSystemPrompt(),
                tools,
                entity.getPriority(),
                Boolean.TRUE.equals(entity.getBuiltIn()));
    }
}

@Repository
interface SkillDefinitionRepository extends JpaRepository<SkillDefinition, Long> {
    List<SkillDefinition> findByAssistantIdAndStatus(String assistantId, String status);

    List<SkillDefinition> findByBuiltInTrueAndStatus(String status);

    Optional<SkillDefinition> findBySkillId(String skillId);
}
