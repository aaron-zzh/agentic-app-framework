package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence.plan;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence.AssistantRuntimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/** {@code ai_executor_plan} 的 JPA 映射。 */
@Getter
@Setter
@Entity
@Table(
        name = "ai_executor_plan",
        uniqueConstraints = {
            @UniqueConstraint(columnNames = {"plan_id"}),
            @UniqueConstraint(columnNames = {"org_id", "plan_id"}),
            @UniqueConstraint(
                    columnNames = {"org_id", "task_id", "node_id", "execution_id", "revision"})
        })
public class ExecutorPlanEntity extends AssistantRuntimeEntity {

    @Column(name = "plan_id", nullable = false, length = 128)
    private String planId;

    @Column(name = "task_id", nullable = false, length = 128)
    private String taskId;

    @Column(name = "node_id", nullable = false, length = 128)
    private String nodeId;

    @Column(name = "execution_id", nullable = false, length = 128)
    private String executionId;

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
    @Column(name = "runtime_lock_version", nullable = false)
    private Long runtimeLockVersion;
}
