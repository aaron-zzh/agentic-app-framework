package com.xuejiai.aaf.module.ai.skill;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 技能视图对象
 *
 * @author AaronZZH & Kiro
 */
public record SkillVO(
        @Schema(description = "主键 ID", example = "1") Long id,
        @Schema(description = "技能唯一标识", example = "chat_skill") String skillId,
        @Schema(description = "所属助手 ID", example = "assistant_001") String assistantId,
        @Schema(description = "技能名称", example = "智能对话") String name,
        @Schema(description = "技能描述") String description,
        @Schema(description = "关联 Agent ID") String agentId,
        @Schema(description = "触发意图") String triggerIntent,
        @Schema(description = "系统提示词") String systemPrompt,
        @Schema(description = "可用工具列表（JSON）") String tools,
        @Schema(description = "优先级", example = "0") Integer priority,
        @Schema(description = "是否内置") Boolean builtIn,
        @Schema(description = "状态", example = "active") String status) {}
