package com.xuejiai.aaf.module.ai.skill;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 更新技能请求（所有字段可选，null 表示不更新）
 *
 * @author AaronZZH & Kiro
 */
public record SkillUpdateDTO(
        @Schema(description = "业务唯一码") @Size(min = 1, max = 100) String code,
        @Schema(description = "技能名称") @Size(min = 1, max = 128) String name,
        @Schema(description = "技能描述") @Size(max = 512) String description,
        @Schema(description = "技能分类") @Size(max = 50) String category,
        @Schema(description = "关联 Agent ID") Long agentId,
        @Schema(description = "触发意图") String triggerIntent,
        @Schema(description = "系统提示词") String systemPrompt,
        @Schema(description = "优先级") Integer priority,
        @Schema(description = "是否公开给当前组织/工作区") Boolean isPublic,
        @Schema(description = "状态：active/inactive") @Pattern(regexp = "active|inactive")
                String status) {}
