package com.xuejiai.aaf.module.system.domain;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;

/** 聊天消息。 */
@Getter
@Setter
@Entity
@Table(name = "sys_chat_message")
@SQLDelete(sql = "UPDATE sys_chat_message SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class ChatMessage extends BaseEntity {

    /** 所属会话 ID */
    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    /** 发送者 ID */
    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    /** 发送者类型：HUMAN / AI */
    @Column(name = "sender_type", nullable = false, length = 16)
    private String senderType;

    /** 消息角色：user / assistant / system */
    @Column(name = "role", nullable = false, length = 20)
    private String role;

    /** 消息内容 */
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    /** 消息消耗的 Token 数 */
    @Column(name = "token_count")
    private Integer tokenCount;

    /** 元数据（JSON 格式，存储模型信息、耗时等） */
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;
}
