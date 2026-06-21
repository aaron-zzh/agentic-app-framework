package com.xuejiai.aaf.module.chat.message.domain;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.common.enums.chat.MessageContentTypeEnum;
import com.xuejiai.aaf.common.enums.chat.MessageSenderTypeEnum;
import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 会话消息实体。
 *
 * @author AaronZZH & Kiro
 */
@Getter
@Setter
@Entity
@Table(name = "conversation_message")
@SQLDelete(
        sql =
                "UPDATE conversation_message SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class ConversationMessage extends BaseEntity {

    /** 所属会话 ID */
    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    /** 发送方 ID（用户ID / agentId / staffId） */
    @Column(name = "sender_id", nullable = false, length = 64)
    private String senderId;

    /** 发送方类型 */
    @Enumerated(EnumType.STRING)
    @Column(name = "sender_type", nullable = false, length = 16)
    private MessageSenderTypeEnum senderType = MessageSenderTypeEnum.HUMAN;

    /** LLM 角色（user / assistant / system / tool） */
    @Column(name = "role", nullable = false, length = 20)
    private String role = "user";

    /** 消息正文 */
    @Column(name = "content", columnDefinition = "text")
    private String content;

    /** 内容类型 */
    @Enumerated(EnumType.STRING)
    @Column(name = "content_type", nullable = false, length = 20)
    private MessageContentTypeEnum contentType = MessageContentTypeEnum.TEXT;

    /** 结构化载荷（tool_call / file 元数据等） */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb")
    private String payload;

    /** 引用回复的消息 ID */
    @Column(name = "reply_to_id")
    private Long replyToId;

    /** 已读记录 {userId: timestamp} */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "read_by", columnDefinition = "jsonb")
    private String readBy;

    /** 是否仅内部可见（坐席内部备注） */
    @Column(name = "is_internal", nullable = false)
    private Boolean isInternal = Boolean.FALSE;

    /** 发送时的用户感知上下文快照 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "awareness_context", columnDefinition = "jsonb")
    private String awarenessContext;

    /** 消耗 Token 数 */
    @Column(name = "token_count")
    private Integer tokenCount;

    /** 扩展元数据 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;
}
