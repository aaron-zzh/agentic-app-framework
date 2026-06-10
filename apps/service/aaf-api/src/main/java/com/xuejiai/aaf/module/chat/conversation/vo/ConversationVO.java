package com.xuejiai.aaf.module.chat.conversation.vo;

import java.time.LocalDateTime;

import com.xuejiai.aaf.module.chat.enums.ConversationStatus;
import com.xuejiai.aaf.module.chat.enums.ConversationType;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 会话响应 VO。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "会话信息")
public record ConversationVO(
        @Schema(description = "ID") Long id,
        @Schema(description = "会话类型") ConversationType type,
        @Schema(description = "标题") String title,
        @Schema(description = "状态") ConversationStatus status,
        @Schema(description = "创建者 ID") Long creatorId,
        @Schema(description = "助理 ID") Long assistantId,
        @Schema(description = "Thread ID") String threadId,
        @Schema(description = "累计 Token") Long totalTokens,
        @Schema(description = "创建时间") LocalDateTime createTime,
        @Schema(description = "更新时间") LocalDateTime updateTime) {}
