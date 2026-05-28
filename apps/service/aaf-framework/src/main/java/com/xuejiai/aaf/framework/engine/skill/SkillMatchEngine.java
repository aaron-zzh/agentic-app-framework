package com.xuejiai.aaf.framework.engine.skill;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.intelligent.core.skill.SkillDef;
import com.xuejiai.aaf.framework.intelligent.core.skill.SkillProvider;

import lombok.RequiredArgsConstructor;

/**
 * 技能匹配引擎——纯领域逻辑。
 *
 * <p>职责：根据用户输入匹配最合适的技能（意图关键词匹配 + 优先级排序）。 数据获取通过 {@link SkillStore} 接口，不直接依赖 Repository/Entity。
 */
@Service
@RequiredArgsConstructor
public class SkillMatchEngine implements SkillProvider {

    private final SkillStore skillStore;

    @Override
    public Optional<SkillDef> match(String assistantId, String userInput) {
        var skills = skillStore.findByAssistant(assistantId);
        if (skills.isEmpty()) {
            skills = skillStore.findBuiltIn();
        }
        return skills.stream()
                .filter(s -> matchesIntent(s, userInput))
                .max(Comparator.comparingInt(SkillStore.SkillRecord::priority))
                .map(this::toSkillDef);
    }

    @Override
    public List<SkillDef> getDefinitions(String assistantId) {
        var userSkills = skillStore.findByAssistant(assistantId);
        var builtinSkills = skillStore.findBuiltIn();
        var userSkillIds = userSkills.stream().map(SkillStore.SkillRecord::skillId).toList();
        var merged = new java.util.ArrayList<>(userSkills);
        builtinSkills.stream()
                .filter(s -> !userSkillIds.contains(s.skillId()))
                .forEach(merged::add);
        return merged.stream().map(this::toSkillDef).toList();
    }

    private boolean matchesIntent(SkillStore.SkillRecord skill, String input) {
        if (skill.triggerIntent() == null || skill.triggerIntent().isBlank()) return false;
        var triggers = skill.triggerIntent().replaceAll("[\\[\\]\"]", "").split(",");
        var lower = input.toLowerCase();
        for (var trigger : triggers) {
            if (lower.contains(trigger.trim().toLowerCase())) return true;
        }
        return false;
    }

    private SkillDef toSkillDef(SkillStore.SkillRecord r) {
        var tools =
                r.tools() != null
                        ? List.of(r.tools().replaceAll("[\\[\\]\"]", "").split(","))
                        : List.<String>of();
        var keywords =
                r.triggerIntent() != null
                        ? List.of(r.triggerIntent().replaceAll("[\\[\\]\"]", "").split(","))
                        : List.<String>of();
        return new SkillDef(
                r.skillId(),
                r.name(),
                r.description(),
                r.agentId(),
                keywords,
                r.systemPrompt(),
                tools,
                r.priority(),
                r.builtIn());
    }
}
