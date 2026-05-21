/**
 * 技能匹配与管理服务。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.assistant;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.xuejiai.aaf.framework.engine.skill.SkillDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/** Skill 注册、匹配、管理。 根据用户意图匹配最合适的 Skill。 */
@Service
@RequiredArgsConstructor
public class SkillMatchService {

    private final SkillRepository skillRepository;

    /** 根据意图匹配 Skill */
    public Optional<SkillDefinition> match(String assistantId, String userInput) {
        var skills = skillRepository.findByAssistantIdAndStatus(assistantId, "active");
        return skills.stream()
                .filter(s -> matchesIntent(s, userInput))
                .max(Comparator.comparingInt(SkillDefinition::getPriority));
    }

    /** 获取 Assistant 的所有技能 */
    public List<SkillDefinition> listSkills(String assistantId) {
        return skillRepository.findByAssistantIdAndStatus(assistantId, "active");
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

interface SkillRepository extends JpaRepository<SkillDefinition, Long> {
    List<SkillDefinition> findByAssistantIdAndStatus(String assistantId, String status);
}
