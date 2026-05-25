package com.xuejiai.aaf.module.system.skill;

import jakarta.validation.constraints.NotBlank;

/** 创建技能请求。 */
public record SkillCreateDTO(
        @NotBlank String skillId,
        String assistantId,
        @NotBlank String name,
        String description,
        String agentId,
        String triggerIntent,
        String systemPrompt,
        String tools,
        Integer priority) {}
