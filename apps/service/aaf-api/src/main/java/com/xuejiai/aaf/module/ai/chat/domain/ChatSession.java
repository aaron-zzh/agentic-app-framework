package com.xuejiai.aaf.module.ai.chat.domain;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 聊天会话实体
 *
 * @author AaronZZH & Kiro
 */
@Getter
@Setter
@Entity
@Table(name = "sys_chat_session")
@SQLDelete(
        sql =
                "UPDATE sys_chat_session SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class ChatSession extends BaseEntity {

    /** 会话标题 */
    @Column(name = "title", length = 200)
    private String title;

    /** 会话类型：LIVECHAT / AI / IM */
    @Column(name = "type", nullable = false, length = 30)
    private String type;

    /** 状态：ACTIVE / CLOSED */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 创建者用户 ID */
    @Column(name = "creator_id", nullable = false)
    private Long creatorId;

    /** AgentScope 路由角色（/agui/runs/{agentRole}） */
    @Column(name = "agent_role", length = 64)
    private String agentRole;

    /** 关联的 Assistant ID（创建对话时写入） */
    @Column(name = "assistant_id", length = 64)
    private String assistantId;

    /** 关联的知识库 ID */
    @Column(name = "knowledge_base_id")
    private Long knowledgeBaseId;

    /** AG-UI 字符串 threadId（用于 /agui/runs 链路按 threadId 关联会话） */
    @Column(name = "thread_id", length = 64)
    private String threadId;

    /** 使用的模型 ID */
    @Column(name = "model_id", length = 100)
    private String modelId;

    /** 累计消耗 Token 数 */
    @Column(name = "total_tokens")
    private Long totalTokens;
}
