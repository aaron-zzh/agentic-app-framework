package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence.plan;

import java.time.Instant;
import java.util.List;

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

/** {@code ai_executor_plan_step} 的 JPA 映射。 */
@Getter
@Setter
@Entity
@Table(
        name = "ai_executor_plan_step",
        uniqueConstraints = {
            @UniqueConstraint(columnNames = {"plan_id", "step_key"}),
            @UniqueConstraint(columnNames = {"plan_id", "ordinal"})
        })
public class ExecutorPlanStepEntity extends AssistantRuntimeEntity {

    @Column(name = "plan_id", nullable = false, length = 128)
    private String planId;

    @Column(name = "step_key", nullable = false, length = 128)
    private String stepKey;

    @Column(nullable = false)
    private Integer ordinal;

    @Column(nullable = false, length = 256)
    private String title;

    @Column(nullable = false, length = 4000)
    private String instruction;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> dependencies;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "required_tools", nullable = false, columnDefinition = "jsonb")
    private List<String> requiredTools;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "completion_criteria", nullable = false, columnDefinition = "jsonb")
    private List<String> completionCriteria;

    @Column(nullable = false, length = 16)
    private String status = "PENDING";

    @Column(name = "result_ref", length = 256)
    private String resultRef;

    @Column(name = "failure_code", length = 64)
    private String failureCode;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Version
    @Column(name = "runtime_lock_version", nullable = false)
    private Long runtimeLockVersion;
}
