package com.xuejiai.aaf.module.system.chat.vo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 聊天会话响应
 *
 * @author AaronZZH & Kiro
 */
public record ChatSessionVO(
        @Schema(description = "会话 ID", example = "1") Long id,
        @Schema(description = "会话标题", example = "新对话") String title,
        @Schema(description = "会话类型", example = "AI") String type,
        @Schema(description = "会话状态", example = "ACTIVE") String status,
        @Schema(description = "创建者用户 ID", example = "1") Long creatorId,
        @Schema(description = "创建时间") LocalDateTime createTime,
        @Schema(description = "更新时间") LocalDateTime updateTime) {}
