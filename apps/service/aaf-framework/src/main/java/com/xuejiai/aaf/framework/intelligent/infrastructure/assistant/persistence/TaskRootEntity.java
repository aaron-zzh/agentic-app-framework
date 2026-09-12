package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.framework.intelligent.assistant.model.CompletionCriteria;
import com.xuejiai.aaf.framework.intelligent.assistant.model.ExecutionContract;
import com.xuejiai.aaf.framework.intelligent.assistant.model.Task;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskCheckpoint;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ControlMode;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ConversationId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.CorrelationId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.RunId;
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
        name = "ai_task",
        uniqueConstraints = {
            @UniqueConstraint(columnNames = {"org_id", "task_id"}),
            @UniqueConstraint(columnNames = {"org_id", "origin_execution_id"})
        })
public class TaskRootEntity extends AssistantRuntimeEntity {

    @Column(name = "user_id", nullable = false, length = 128)
    private String userId;

    @Column(name = "task_id", nullable = false, length = 128)
    private String taskId;

    @Column(name = "conversation_id", nullable = false, length = 128)
    private String conversationId;

    @Column(name = "origin_execution_id", length = 128)
    private String originExecutionId;

    @Column(name = "origin_run_id", length = 128)
    private String originRunId;

    @Column(name = "origin_correlation_id", length = 128)
    private String originCorrelationId;

    @Column(name = "origin_input_ref", length = 512)
    private String originInputRef;

    @Column(name = "public_context_ref", length = 512)
    private String publicContextRef;

    @Column(nullable = false, length = 24)
    private String source;

    @Column(nullable = false)
    private Integer priority;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "control_mode", nullable = false, length = 24)
    private String controlMode;

    @Column(name = "responsible_actor_kind", nullable = false, length = 16)
    private String responsibleActorKind;

    @Column(name = "responsible_actor_id", nullable = false, length = 128)
    private String responsibleActorId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "execution_contract", nullable = false, columnDefinition = "jsonb")
    private ExecutionContract executionContract;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "completion_criteria", nullable = false, columnDefinition = "jsonb")
    private CompletionCriteria completionCriteria;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "budget_usage", nullable = false, columnDefinition = "jsonb")
    private Task.BudgetUsage budgetUsage;

    @Column(name = "current_plan_id", length = 128)
    private String currentPlanId;

    @Column(name = "current_plan_revision")
    private Integer currentPlanRevision;

    @Column(name = "current_root_execution_id", length = 128)
    private String currentRootExecutionId;

    @Column(name = "current_root_attempt_no", nullable = false)
    private Integer currentRootAttemptNo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "root_result", columnDefinition = "jsonb")
    private Task.RootResult rootResult;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "checkpoint", nullable = false, columnDefinition = "jsonb")
    private TaskCheckpoint checkpoint;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "recovery_point", columnDefinition = "jsonb")
    private Task.RecoveryPoint recoveryPoint;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "runtime_lock_version", nullable = false)
    private Long runtimeLockVersion;

    public Task getTask() {
        return new Task(
                new TenantId(getTenantId()),
                new UserId(userId),
                new TaskId(taskId),
                new ConversationId(conversationId),
                originExecutionId == null ? null : new ExecutionId(originExecutionId),
                originRunId == null ? null : new RunId(originRunId),
                originCorrelationId == null ? null : new CorrelationId(originCorrelationId),
                originInputRef,
                publicContextRef,
                Task.Source.valueOf(source),
                priority,
                Task.Status.valueOf(status),
                ControlMode.valueOf(controlMode),
                new Task.Owner(Task.OwnerKind.valueOf(responsibleActorKind), responsibleActorId),
                executionContract,
                completionCriteria,
                budgetUsage,
                currentPlanId,
                currentPlanRevision,
                currentRootExecutionId == null ? null : new ExecutionId(currentRootExecutionId),
                currentRootAttemptNo,
                rootResult,
                checkpoint,
                recoveryPoint,
                createdAt,
                updatedAt);
    }

    public void setTask(Task task) {
        setTenantId(task.tenantId().value());
        userId = task.userId().value();
        taskId = task.taskId().value();
        conversationId = task.conversationId().value();
        originExecutionId =
                task.originExecutionId() == null ? null : task.originExecutionId().value();
        originRunId = task.originRunId() == null ? null : task.originRunId().value();
        originCorrelationId =
                task.originCorrelationId() == null ? null : task.originCorrelationId().value();
        originInputRef = task.originInputRef();
        publicContextRef = task.publicContextRef();
        source = task.source().name();
        priority = task.priority();
        status = task.status().name();
        controlMode = task.controlMode().name();
        responsibleActorKind = task.owner().kind().name();
        responsibleActorId = task.owner().ownerId();
        executionContract = task.contract();
        completionCriteria = task.completionCriteria();
        budgetUsage = task.budgetUsage();
        currentPlanId = task.currentPlanId();
        currentPlanRevision = task.currentPlanRevision();
        currentRootExecutionId =
                task.currentRootExecutionId() == null
                        ? null
                        : task.currentRootExecutionId().value();
        currentRootAttemptNo = task.currentRootAttemptNo();
        rootResult = task.rootResult();
        checkpoint = task.checkpoint();
        recoveryPoint = task.recoveryPoint();
        createdAt = task.createdAt();
        updatedAt = task.updatedAt();
    }
}
