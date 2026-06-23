package com.xuejiai.aaf.module.ai.skill;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 创建技能请求
 *
 * @author AaronZZH & Kiro
 */
public record SkillCreateDTO(
        @Schema(description = "业务唯一码") String code,
        @Schema(description = "技能名称") @NotBlank String name,
        @Schema(description = "技能描述") String description,
        @Schema(description = "技能分类") String category,
        @Schema(description = "关联 Agent ID") Long agentId,
        @Schema(description = "触发意图") String triggerIntent,
        @Schema(description = "系统提示词") String systemPrompt,
        @Schema(description = "优先级") Integer priority) {}
