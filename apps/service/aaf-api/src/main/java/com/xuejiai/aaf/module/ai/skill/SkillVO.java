package com.xuejiai.aaf.module.ai.skill;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 技能视图对象
 *
 * @author AaronZZH & Kiro
 */
public record SkillVO(
        @Schema(description = "主键 ID") Long id,
        @Schema(description = "业务唯一码（前端 deep-link 用，可为空）") String code,
        @Schema(description = "技能名称") String name,
        @Schema(description = "技能描述") String description,
        @Schema(description = "技能分类") String category,
        @Schema(description = "关联 Agent ID") Long agentId,
        @Schema(description = "触发意图") String triggerIntent,
        @Schema(description = "系统提示词") String systemPrompt,
        @Schema(description = "优先级") Integer priority,
        @Schema(description = "是否内置") Boolean builtIn,
        @Schema(description = "状态") String status) {}
