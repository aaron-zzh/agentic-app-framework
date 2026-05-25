package com.xuejiai.aaf.module.system.skill;

/** 技能视图对象。 */
public record SkillVO(
        Long id,
        String skillId,
        String assistantId,
        String name,
        String description,
        String agentId,
        String triggerIntent,
        String systemPrompt,
        String tools,
        Integer priority,
        Boolean builtIn,
        String status) {}
