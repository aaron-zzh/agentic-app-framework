package com.xuejiai.aaf.module.ai.skill;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建技能请求
 *
 * @author AaronZZH & Kiro
 */
public record SkillCreateDTO(
        @Schema(description = "业务唯一码；为空时由后端生成") @Size(max = 100) String code,
        @Schema(description = "技能名称") @NotBlank @Size(max = 128) String name,
        @Schema(description = "技能描述") @Size(max = 512) String description,
        @Schema(description = "技能分类") @Size(max = 50) String category,
        @Schema(description = "关联 Agent ID") Long agentId,
        @Schema(description = "触发意图") String triggerIntent,
        @Schema(description = "系统提示词") String systemPrompt,
        @Schema(description = "优先级") Integer priority,
        @Schema(description = "是否公开给当前组织/工作区") Boolean isPublic) {}
