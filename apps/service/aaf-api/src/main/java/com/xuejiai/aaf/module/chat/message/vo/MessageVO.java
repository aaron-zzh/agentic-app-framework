package com.xuejiai.aaf.module.chat.message.vo;

import java.time.LocalDateTime;

import com.xuejiai.aaf.module.chat.enums.MessageContentType;
import com.xuejiai.aaf.module.chat.enums.MessageSenderType;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 消息响应 VO。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "消息信息")
public record MessageVO(
        @Schema(description = "ID") Long id,
        @Schema(description = "会话 ID") Long conversationId,
        @Schema(description = "发送方 ID") String senderId,
        @Schema(description = "发送方类型") MessageSenderType senderType,
        @Schema(description = "LLM 角色") String role,
        @Schema(description = "消息内容") String content,
        @Schema(description = "内容类型") MessageContentType contentType,
        @Schema(description = "是否内部可见") Boolean isInternal,
        @Schema(description = "Token 数") Integer tokenCount,
        @Schema(description = "创建时间") LocalDateTime createTime) {}
