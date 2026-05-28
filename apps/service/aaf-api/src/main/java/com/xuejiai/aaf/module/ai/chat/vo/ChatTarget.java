package com.xuejiai.aaf.module.ai.chat.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 聊天目标：决定消息路由到哪个处理器
 *
 * @author AaronZZH & Kiro
 */
public record ChatTarget(
        @Schema(description = "目标类型", example = "ai") @NotBlank String type,
        @Schema(description = "Agent 角色（kiro 时使用）", example = "architect") String agentRole,
        @Schema(description = "目标用户 ID（user 时使用）", example = "1") Long userId) {}
