package com.xuejiai.aaf.module.livechat.vo;

import java.time.LocalDateTime;

import com.xuejiai.aaf.module.chat.enums.MessageContentType;
import com.xuejiai.aaf.module.chat.enums.MessageSenderType;

/** 消息 VO（已迁移至 chat 模块枚举）。 */
public record ChatMessageVO(
        Long id,
        Long conversationId,
        MessageSenderType senderType,
        String senderId,
        MessageContentType contentType,
        String content,
        Boolean isInternal,
        LocalDateTime createTime) {}
