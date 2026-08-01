package com.xuejiai.aaf.framework.intelligent.assistant.hitl;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/**
 * 工具层人工确认持久化实体（M36）。
 *
 * <p>替代原 {@code HumanApprovalService} 的内存 Map——内存态在重启/多实例下丢失，且旧链没有任何消费方， 审批建了没人能处理。
 *
 * <p>与任务级 {@code ai_hitl_approval} 分工：本表面向会话/工作流级的工具确认（无 AssistantTask 上下文）， 决定后由 {@code
 * ToolApprovalGrantListener} 回写会话级工具授权，不驱动任务状态迁移。
 *
 * <p>不继承 {@code BaseEntity}：审批是追加+一次决定的流水，不需要软删除与组织过滤（作用域由 scopeKey/userId 表达）。
 */
@Getter
@Setter
@Entity
@Table(name = "ai_tool_approval")
public class ToolApprovalEntity {

    @Id
    @Column(name = "approval_id", length = 64)
    private String approvalId;

    /** 授权作用域键：sessionId 或 workflow:<processInstanceId>；无会话时为 "-" */
    @Column(name = "scope_key", nullable = false, length = 128)
    private String scopeKey;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "approval_type", nullable = false, length = 32)
    private String approvalType;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "subject_type", length = 32)
    private String subjectType;

    @Column(name = "subject_key", length = 200)
    private String subjectKey;

    @Column(name = "risk_level", length = 16)
    private String riskLevel;

    @Column(name = "confidence")
    private Double confidence;

    @Column(name = "grant_scope", nullable = false, length = 16)
    private String grantScope;

    /** 原始上下文（JSON 文本），供渠道卡片与审计还原细节 */
    @Column(name = "context_json")
    private String contextJson;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "decision_reason", length = 500)
    private String decisionReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decided_by")
    private Long decidedBy;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;
}
