package com.xuejiai.aaf.framework.engine.skill;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.intelligent.assistant.role.RoleStore;
import com.xuejiai.aaf.framework.intelligent.core.skill.SkillDef;
import com.xuejiai.aaf.framework.intelligent.core.skill.SkillProvider;

import lombok.RequiredArgsConstructor;

/**
 * 技能匹配引擎——纯领域逻辑。
 *
 * <p>职责：根据用户输入匹配最合适的技能（意图关键词匹配 + 优先级排序）。 技能为全局/可复用定义，助理可用的技能经角色挂载（assistant → ai_assistant_role →
 * role.skillIds），数据获取通过 {@link SkillStore} / {@link RoleStore} 接口，不直接依赖 Repository/Entity。
 */
@Service
@RequiredArgsConstructor
public class SkillMatchEngine implements SkillProvider {

    private final SkillStore skillStore;
    private final RoleStore roleStore;

    @Override
    public Optional<SkillDef> match(Long assistantId, String userInput) {
        var skills = resolveSkills(assistantId);
        if (skills.isEmpty()) {
            skills = skillStore.findBuiltIn();
        }
        return skills.stream()
                .filter(s -> matchesIntent(s, userInput))
                .max(Comparator.comparingInt(SkillStore.SkillRecord::priority))
                .map(this::toSkillDef);
    }

    @Override
    public List<SkillDef> getDefinitions(Long assistantId) {
        var roleSkills = resolveSkills(assistantId);
        var builtinSkills = skillStore.findBuiltIn();
        // 以 skillId 去重合并，角色技能优先
        var merged = new LinkedHashMap<Long, SkillStore.SkillRecord>();
        roleSkills.forEach(s -> merged.put(s.skillId(), s));
        builtinSkills.forEach(s -> merged.putIfAbsent(s.skillId(), s));
        return merged.values().stream().map(this::toSkillDef).toList();
    }

    /** 解析助理可用的技能：经角色挂载（assistant → role → role.skillIds → 技能定义）。 */
    private List<SkillStore.SkillRecord> resolveSkills(Long assistantId) {
        if (assistantId == null) return List.of();
        return roleStore.getRoleIdsByAssistant(assistantId).stream()
                .flatMap(roleId -> roleStore.getSkillIds(roleId).stream())
                .distinct()
                .map(skillStore::findBySkillId)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
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
                r.priority(),
                r.builtIn());
    }
}
