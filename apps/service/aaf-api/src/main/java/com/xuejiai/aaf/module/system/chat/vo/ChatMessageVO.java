package com.xuejiai.aaf.module.system.chat.vo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 聊天消息响应
 *
 * @author AaronZZH & Kiro
 */
public record ChatMessageVO(
        @Schema(description = "消息 ID", example = "1") Long id,
        @Schema(description = "会话 ID", example = "1") Long sessionId,
        @Schema(description = "发送者 ID", example = "1") Long senderId,
        @Schema(description = "发送者类型", example = "HUMAN") String senderType,
        @Schema(description = "消息角色", example = "user") String role,
        @Schema(description = "消息内容", example = "你好") String content,
        @Schema(description = "创建时间") LocalDateTime createTime) {}
