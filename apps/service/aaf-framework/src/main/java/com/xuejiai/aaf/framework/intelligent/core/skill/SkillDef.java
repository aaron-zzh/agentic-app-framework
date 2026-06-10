package com.xuejiai.aaf.framework.intelligent.core.skill;

import java.util.List;

/** 技能定义数据契约（纯数据，无 JPA 依赖）。 engine/skill 的 SkillDefinition @Entity 映射到此 Record 对外暴露。 */
public record SkillDef(
        Long skillId,
        String name,
        String description,
        Long agentId,
        List<String> triggerKeywords,
        String systemPrompt,
        int priority,
        boolean builtIn) {}
