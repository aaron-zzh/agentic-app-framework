package com.xuejiai.aaf.module.system.chat.vo;

import java.time.LocalDateTime;

/** 聊天消息响应。 */
public record ChatMessageVO(
        Long id,
        Long sessionId,
        Long senderId,
        String senderType,
        String role,
        String content,
        LocalDateTime createTime) {}
