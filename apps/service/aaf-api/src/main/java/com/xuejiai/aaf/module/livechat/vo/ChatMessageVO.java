package com.xuejiai.aaf.module.livechat.vo;

import java.time.LocalDateTime;

import com.xuejiai.aaf.common.enums.channel.MessageTypeEnum;
import com.xuejiai.aaf.common.enums.livechat.SenderTypeEnum;

/** 消息 VO。 */
public record ChatMessageVO(
        Long id,
        Long sessionId,
        SenderTypeEnum senderType,
        Long senderId,
        MessageTypeEnum messageType,
        String content,
        Boolean internal,
        LocalDateTime createTime) {}
