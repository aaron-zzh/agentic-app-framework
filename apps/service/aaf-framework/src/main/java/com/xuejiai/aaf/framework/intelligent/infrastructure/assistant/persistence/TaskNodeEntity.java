package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence;

import java.util.Map;
import java.util.Set;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskNodeDefinition;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskPlan;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.SessionId;

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
        name = "ai_task_node",
        uniqueConstraints =
                @UniqueConstraint(
                        columnNames = {"org_id", "task_id", "plan_id", "plan_revision", "node_id"}))
public class TaskNodeEntity extends AssistantRuntimeEntity {

    @Column(name = "task_id", nullable = false, length = 128)
    private String taskId;

    @Column(name = "plan_id", nullable = false, length = 128)
    private String planId;

    @Column(name = "plan_revision", nullable = false)
    private Integer planRevision;

    @Column(name = "node_id", nullable = false, length = 128)
    private String nodeId;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "current_execution_id", length = 128)
    private String currentExecutionId;

    @Column(name = "current_session_id", length = 128)
    private String currentSessionId;

    @Column(name = "current_attempt", nullable = false)
    private Integer currentAttempt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "node_payload", nullable = false, columnDefinition = "jsonb")
    private TaskNodeDefinition definition;

    @Column(name = "result_text")
    private String result;

    @Column(name = "failure_text", length = 1000)
    private String failure;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "clarified_parameters", nullable = false, columnDefinition = "jsonb")
    private Map<String, String> clarifiedParameters = Map.of();

    @Version
    @Column(name = "runtime_lock_version", nullable = false)
    private Long runtimeLockVersion;

    public TaskPlan.TaskNode toDomain(Set<String> dependencies) {
        return new TaskPlan.TaskNode(
                nodeId,
                definition.kind(),
                definition.description(),
                dependencies,
                definition.inputBindings(),
                definition.roleKey(),
                definition.skillKey(),
                definition.assistantTarget(),
                definition.modelSelection(),
                TaskPlan.Status.valueOf(status),
                definition.retryable(),
                currentAttempt,
                definition.maxAttempts(),
                currentExecutionId == null ? null : new ExecutionId(currentExecutionId),
                currentSessionId == null ? null : new SessionId(currentSessionId),
                result,
                failure,
                clarifiedParameters == null ? Map.of() : Map.copyOf(clarifiedParameters));
    }
}
