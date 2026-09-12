package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskPlan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
        name = "ai_task_plan",
        uniqueConstraints = {
            @UniqueConstraint(columnNames = {"org_id", "task_id", "plan_id", "plan_revision"}),
            @UniqueConstraint(columnNames = {"org_id", "task_id", "plan_revision"})
        })
public class TaskPlanEntity extends AssistantRuntimeEntity {

    @Column(name = "task_id", nullable = false, length = 128)
    private String taskId;

    @Column(name = "plan_id", nullable = false, length = 128)
    private String planId;

    @Column(name = "plan_revision", nullable = false)
    private Integer planRevision;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "max_parallelism", nullable = false)
    private Integer maxParallelism;

    @Column(name = "failure_policy", nullable = false, length = 16)
    private String failurePolicy;

    @Column(name = "graph_hash", nullable = false, length = 64)
    private String graphHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "goal_payload", nullable = false, columnDefinition = "jsonb")
    private TaskPlan.Goal goal;

    @Version
    @Column(name = "runtime_lock_version", nullable = false)
    private Long runtimeLockVersion;
}
