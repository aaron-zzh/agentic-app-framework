package com.xuejiai.aaf.module.system.domain;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;

/** 聊天会话。 */
@Getter
@Setter
@Entity
@Table(name = "sys_chat_session")
@SQLDelete(sql = "UPDATE sys_chat_session SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
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

    /** 关联的 Agent ID */
    @Column(name = "agent_id", length = 100)
    private String agentId;

    /** 使用的模型 ID */
    @Column(name = "model_id", length = 100)
    private String modelId;

    /** 累计消耗 Token 数 */
    @Column(name = "total_tokens")
    private Long totalTokens;
}
