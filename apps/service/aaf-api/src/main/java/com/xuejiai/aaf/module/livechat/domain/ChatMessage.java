package com.xuejiai.aaf.module.livechat.domain;

import com.xuejiai.aaf.common.enums.channel.MessageTypeEnum;
import com.xuejiai.aaf.common.enums.livechat.SenderTypeEnum;
import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 客服会话消息实体。 */
@Getter
@Setter
@Entity(name = "LivechatChatMessage")
@Table(name = "chat_message")
public class ChatMessage extends BaseEntity {

    /** 所属会话 ID */
    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    /** 发送者类型 */
    @Enumerated(EnumType.STRING)
    @Column(name = "sender_type", nullable = false, length = 16)
    private SenderTypeEnum senderType;

    /** 发送者 ID（user_id / agent_id / staff_id） */
    @Column(name = "sender_id")
    private Long senderId;

    /** 消息类型 */
    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 16)
    private MessageTypeEnum messageType;

    /** 消息内容 */
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    /** 是否内部消息（坐席间讨论，不对用户可见） */
    @Column(name = "internal", nullable = false)
    private Boolean internal = false;
}
