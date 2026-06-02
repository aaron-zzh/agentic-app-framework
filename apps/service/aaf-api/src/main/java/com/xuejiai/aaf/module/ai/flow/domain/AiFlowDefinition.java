package com.xuejiai.aaf.module.ai.flow.domain;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * AI 工作流定义。
 *
 * <p>存储 flow-editor 编辑态 JSON，发布后部署到 Flowable 引擎执行。 通过 {@code agentCallable} 控制是否允许智能体自动调用。
 */
@Getter
@Setter
@Entity
@Table(name = "ai_flow_definition")
@SQLDelete(
        sql =
                "UPDATE ai_flow_definition SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ? AND version = ?")
public class AiFlowDefinition extends BaseEntity {

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "description", length = 512)
    private String description;

    /** 流程模式：CHAT / COMPLETION / AGENT */
    @Column(name = "mode", nullable = false, length = 32)
    private String mode = "CHAT";

    /** 编辑态 JSON（ReactFlow 节点+连线） */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "definition", nullable = false, columnDefinition = "jsonb")
    private String definition = "{}";

    /** 发布状态：DRAFT / PUBLISHED / DISABLED */
    @Column(name = "status", nullable = false, length = 16)
    private String status = "DRAFT";

    /** Flowable deployment ID，发布后有值 */
    @Column(name = "deployment_id", length = 64)
    private String deploymentId;

    @Column(name = "published_at")
    private java.time.LocalDateTime publishedAt;

    /** 是否允许智能体自动调用 */
    @Column(name = "agent_callable", nullable = false)
    private Boolean agentCallable = false;

    /** 智能体调用前是否需要用户确认 */
    @Column(name = "require_confirm", nullable = false)
    private Boolean requireConfirm = true;
}
