package com.xuejiai.aaf.module.ai.skill;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 更新技能请求（所有字段可选，null 表示不更新）
 *
 * @author AaronZZH & Kiro
 */
public record SkillUpdateDTO(
        @Schema(description = "技能名称") String name,
        @Schema(description = "技能描述") String description,
        @Schema(description = "关联 Agent ID") Long agentId,
        @Schema(description = "触发意图") String triggerIntent,
        @Schema(description = "系统提示词") String systemPrompt,
        @Schema(description = "优先级") Integer priority) {}
