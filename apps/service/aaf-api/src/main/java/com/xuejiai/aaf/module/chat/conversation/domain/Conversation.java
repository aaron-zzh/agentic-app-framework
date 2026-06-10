package com.xuejiai.aaf.module.chat.conversation.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.module.chat.enums.ConversationStatus;
import com.xuejiai.aaf.module.chat.enums.ConversationType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 统一会话实体：AI对话 / 客服会话 / IM（含代理间）。
 *
 * @author AaronZZH & Kiro
 */
@Getter
@Setter
@Entity
@Table(name = "conversation")
@SQLDelete(
        sql =
                "UPDATE conversation SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ? AND version = ?")
public class Conversation extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 16)
    private ConversationType type = ConversationType.AI;

    @Column(name = "title", length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ConversationStatus status = ConversationStatus.ACTIVE;

    @Column(name = "creator_id", nullable = false)
    private Long creatorId;

    /** AI 对话快捷字段，指向 ai_assistant.id */
    @Column(name = "assistant_id")
    private Long assistantId;

    /** AG-UI threadId */
    @Column(name = "thread_id", length = 64)
    private String threadId;

    @Column(name = "model_id", length = 100)
    private String modelId;

    @Column(name = "total_tokens")
    private Long totalTokens = 0L;

    /** AgentScope 路由角色 */
    @Column(name = "agent_role", length = 64)
    private String agentRole;

    /** 直连知识库 ID */
    @Column(name = "knowledge_base_id")
    private Long knowledgeBaseId;

    /** 当前服务坐席 ID（LIVECHAT） */
    @Column(name = "staff_id")
    private Long staffId;

    @Column(name = "priority")
    private Integer priority = 3;

    /** 渠道扩展信息（LIVECHAT）：{channel_type, external_user_id, skill_group, tags} */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "channel_extension", columnDefinition = "jsonb")
    private String channelExtension;

    /** 会话创建时的用户感知上下文 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "context_snapshot", columnDefinition = "jsonb")
    private String contextSnapshot;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;
}
