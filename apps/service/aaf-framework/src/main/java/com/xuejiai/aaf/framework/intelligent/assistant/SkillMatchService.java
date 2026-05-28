/**
 * 技能匹配服务——通过 Role 关联获取技能。
 *
 * <p>链路：Assistant → Role.skillIds → SkillDefinition → 匹配
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.assistant;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.engine.skill.SkillDefinition;
import com.xuejiai.aaf.framework.engine.skill.SkillStore;
import com.xuejiai.aaf.framework.intelligent.assistant.role.RoleStore;

import lombok.RequiredArgsConstructor;

/**
 * Skill 匹配服务。
 *
 * <p>通过 Assistant → Role → Role.skillIds 获取该 Assistant 可用的技能列表， 再按用户输入匹配最合适的技能。
 */
@Service
@RequiredArgsConstructor
public class SkillMatchService {

    private final AssistantDefinitionRepository assistantRepo;
    private final RoleStore roleStore;
    private final SkillStore skillStore;

    /** 根据用户输入匹配 Skill（走 Role 关联路径）。 */
    public Optional<SkillDefinition> match(String assistantId, String userInput) {
        var skills = getSkillsForAssistant(assistantId);
        return skills.stream()
                .filter(s -> matchesIntent(s, userInput))
                .max(Comparator.comparingInt(SkillDefinition::getPriority));
    }

    /** 获取 Assistant 的所有可用技能。 */
    public List<SkillDefinition> listSkills(String assistantId) {
        return getSkillsForAssistant(assistantId);
    }

    private List<SkillDefinition> getSkillsForAssistant(String assistantId) {
        // 1. 查 Assistant 定义获取 roleId
        var assistant = assistantRepo.findByAssistantId(assistantId).orElse(null);
        if (assistant == null) return List.of();

        // 2. 查 Role 获取 skillIds
        var skillIds = roleStore.getSkillIds(assistant.getRoleId());
        if (skillIds.isEmpty()) return List.of();

        // 3. 按 skillIds 查技能定义
        return skillIds.stream()
                .map(skillStore::findBySkillId)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(this::toEntity)
                .toList();
    }

    private SkillDefinition toEntity(SkillStore.SkillRecord r) {
        var e = new SkillDefinition();
        e.setSkillId(r.skillId());
        e.setAssistantId(r.assistantId());
        e.setName(r.name());
        e.setDescription(r.description());
        e.setAgentId(r.agentId());
        e.setTriggerIntent(r.triggerIntent());
        e.setSystemPrompt(r.systemPrompt());
        e.setTools(r.tools());
        e.setPriority(r.priority());
        e.setBuiltIn(r.builtIn());
        return e;
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
}
