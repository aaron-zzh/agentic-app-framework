package com.xuejiai.aaf.module.system.skill;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 创建技能请求
 *
 * @author AaronZZH & Kiro
 */
public record SkillCreateDTO(
        @Schema(description = "技能唯一标识", example = "chat_skill") @NotBlank String skillId,
        @Schema(description = "所属助手 ID") String assistantId,
        @Schema(description = "技能名称", example = "智能对话") @NotBlank String name,
        @Schema(description = "技能描述") String description,
        @Schema(description = "关联 Agent ID") String agentId,
        @Schema(description = "触发意图") String triggerIntent,
        @Schema(description = "系统提示词") String systemPrompt,
        @Schema(description = "可用工具列表（JSON）") String tools,
        @Schema(description = "优先级", example = "0") Integer priority) {}
