package com.xuejiai.aaf.module.system.skill;

/** 更新技能请求（所有字段可选，null 表示不更新）。 */
public record SkillUpdateDTO(
        String name,
        String description,
        String agentId,
        String triggerIntent,
        String systemPrompt,
        String tools,
        Integer priority) {}
