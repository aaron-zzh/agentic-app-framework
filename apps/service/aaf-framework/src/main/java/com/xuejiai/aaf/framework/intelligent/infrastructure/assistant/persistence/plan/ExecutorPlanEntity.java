package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence.plan;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/**
 * {@code ai_executor_plan} 的 JPA 映射。
 *
 * <p>用显式关系列而非单一 JSONB（对比 {@code TaskBoardEntity.board_payload}），因为计划需要独立的 revision 唯一约束、 审批人留痕列和
 * {@code REVIEW_REQUIRED} 队列索引——这些是 SQL 层不变量，JSONB 表达式索引无法直接承担唯一约束。
 */
@Getter
@Setter
@Entity
@Table(
        name = "ai_executor_plan",
        uniqueConstraints = {
            @UniqueConstraint(columnNames = {"plan_id"}),
            @UniqueConstraint(columnNames = {"tenant_id", "task_id", "board_id", "revision"})
        })
public class ExecutorPlanEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_id", nullable = false, length = 128)
    private String planId;

    @Column(name = "tenant_id", nullable = false, length = 128)
    private String tenantId;

    @Column(name = "task_id", nullable = false, length = 128)
    private String taskId;

    @Column(name = "board_id", nullable = false, length = 128)
    private String boardId;

    @Column(name = "executor_agent_id", nullable = false, length = 128)
    private String executorAgentId;

    @Column(nullable = false)
    private Integer revision;

    @Column(nullable = false, length = 24)
    private String status;

    @Column(nullable = false, length = 4000)
    private String goal;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "policy_snapshot", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> policySnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> risks;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> verification;

    @Column(name = "submitted_by_type", length = 16)
    private String submittedByType;

    @Column(name = "submitted_by_id", length = 128)
    private String submittedById;

    @Column(name = "reviewed_by_type", length = 16)
    private String reviewedByType;

    @Column(name = "reviewed_by_id", length = 128)
    private String reviewedById;

    @Column(name = "review_comment", length = 2000)
    private String reviewComment;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "lock_version", nullable = false)
    private Long lockVersion;
}
