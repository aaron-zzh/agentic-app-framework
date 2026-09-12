package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.framework.intelligent.assistant.application.AssistantCommand;
import com.xuejiai.aaf.framework.intelligent.assistant.model.Execution;
import com.xuejiai.aaf.framework.intelligent.assistant.model.Task;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ConversationId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.CorrelationId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.RunId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.SessionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;

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
        name = "ai_task_execution",
        uniqueConstraints = {
            @UniqueConstraint(columnNames = {"org_id", "execution_id"}),
            @UniqueConstraint(
                    columnNames = {
                        "org_id",
                        "task_id",
                        "plan_id",
                        "plan_revision",
                        "node_id",
                        "attempt_no"
                    })
        })
public class TaskExecutionEntity extends AssistantRuntimeEntity {

    @Column(name = "user_id", nullable = false, length = 128)
    private String userId;

    @Column(name = "conversation_id", nullable = false, length = 128)
    private String conversationId;

    @Column(name = "task_id", length = 128)
    private String taskId;

    @Column(name = "plan_id", length = 128)
    private String planId;

    @Column(name = "plan_revision")
    private Integer planRevision;

    @Column(name = "node_id", length = 128)
    private String nodeId;

    @Column(name = "execution_id", nullable = false, length = 128)
    private String executionId;

    @Column(name = "session_id", nullable = false, length = 128)
    private String sessionId;

    @Column(name = "run_id", nullable = false, length = 128)
    private String runId;

    @Column(name = "correlation_id", nullable = false, length = 128)
    private String correlationId;

    @Column(name = "parent_execution_id", length = 128)
    private String parentExecutionId;

    @Column(name = "predecessor_execution_id", length = 128)
    private String predecessorExecutionId;

    @Column(nullable = false, length = 24)
    private String scope;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "promotion_state", nullable = false, length = 16)
    private String promotionState;

    @Column(name = "side_effect_epoch", nullable = false)
    private Long sideEffectEpoch;

    @Column(name = "attempt_no", nullable = false)
    private Integer attemptNo;

    @Column(name = "state_slot_id", nullable = false, length = 128)
    private String stateSlotId;

    @Column(name = "responsible_actor_kind", nullable = false, length = 24)
    private String responsibleActorKind;

    @Column(name = "responsible_actor_id", nullable = false, length = 128)
    private String responsibleActorId;

    @Column(name = "consecutive_failures", nullable = false)
    private Integer consecutiveFailures;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "command_payload", columnDefinition = "jsonb")
    private AssistantCommand command;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "runtime_lock_version", nullable = false)
    private Long runtimeLockVersion;

    public Execution getExecution() {
        return new Execution(
                new TenantId(getTenantId()),
                new UserId(userId),
                new ConversationId(conversationId),
                taskId == null ? null : new TaskId(taskId),
                planId,
                planRevision,
                nodeId,
                new ExecutionId(executionId),
                new SessionId(sessionId),
                new RunId(runId),
                new CorrelationId(correlationId),
                parentExecutionId == null ? null : new ExecutionId(parentExecutionId),
                predecessorExecutionId == null ? null : new ExecutionId(predecessorExecutionId),
                Execution.Scope.valueOf(scope),
                attemptNo,
                stateSlotId,
                Execution.Status.valueOf(status),
                Execution.PromotionState.valueOf(promotionState),
                sideEffectEpoch,
                new Task.Owner(Task.OwnerKind.valueOf(responsibleActorKind), responsibleActorId),
                consecutiveFailures,
                createdAt,
                updatedAt);
    }

    public void setExecution(Execution execution) {
        setTenantId(execution.tenantId().value());
        userId = execution.userId().value();
        conversationId = execution.conversationId().value();
        taskId = execution.taskId() == null ? null : execution.taskId().value();
        planId = execution.planId();
        planRevision = execution.planRevision();
        nodeId = execution.nodeId();
        executionId = execution.executionId().value();
        sessionId = execution.sessionId().value();
        runId = execution.runId().value();
        correlationId = execution.correlationId().value();
        parentExecutionId =
                execution.parentExecutionId() == null
                        ? null
                        : execution.parentExecutionId().value();
        predecessorExecutionId =
                execution.predecessorExecutionId() == null
                        ? null
                        : execution.predecessorExecutionId().value();
        scope = execution.scope().name();
        status = execution.status().name();
        promotionState = execution.promotionState().name();
        sideEffectEpoch = execution.sideEffectEpoch();
        attemptNo = execution.attemptNo();
        stateSlotId = execution.stateSlotId();
        responsibleActorKind = execution.ownerSnapshot().kind().name();
        responsibleActorId = execution.ownerSnapshot().ownerId();
        consecutiveFailures = execution.consecutiveFailures();
        createdAt = execution.createdAt();
        updatedAt = execution.updatedAt();
    }
}
