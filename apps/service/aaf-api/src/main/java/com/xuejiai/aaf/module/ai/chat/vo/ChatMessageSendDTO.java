package com.xuejiai.aaf.module.ai.chat.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 发送聊天消息请求
 *
 * @author AaronZZH & Kiro
 */
public record ChatMessageSendDTO(
        @Schema(description = "会话 ID", example = "1") @NotNull Long sessionId,
        @Schema(description = "消息内容", example = "你好") @NotBlank String content) {}
