package com.xuejiai.aaf.framework.intelligent.infrastructure.governance.persistence;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.intelligent.assistant.application.AssistantCommand;
import com.xuejiai.aaf.framework.intelligent.assistant.model.HumanApproval;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskRecoveryPort;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;

/** approvalId 恢复作业的 JPA 实现；不轮询，由事件或显式入口领取。 */
public final class JpaTaskRecoveryAdapter implements TaskRecoveryPort {

    private static final Duration RECOVERY_LEASE = Duration.ofHours(1);

    private final TaskRecoveryCommandRepository commands;
    private final HitlRecoveryRepository recoveries;

    public JpaTaskRecoveryAdapter(
            TaskRecoveryCommandRepository commands, HitlRecoveryRepository recoveries) {
        this.commands = Objects.requireNonNull(commands, "commands 不能为空");
        this.recoveries = Objects.requireNonNull(recoveries, "recoveries 不能为空");
    }

    @Override
    @Transactional
    public void saveCommand(AssistantCommand command) {
        var key = command.tenantId().value() + ":" + command.taskId().value();
        var entity =
                commands.findByTenantIdAndTaskId(
                                command.tenantId().value(), command.taskId().value())
                        .orElseGet(TaskRecoveryCommandEntity::new);
        entity.setCommandKey(key);
        entity.setTenantId(command.tenantId().value());
        entity.setTaskId(command.taskId().value());
        entity.setCommand(command);
        entity.setUpdatedAt(command.requestedAt());
        commands.saveAndFlush(entity);
    }

    @Override
    @Transactional
    public RecoveryJob schedule(HumanApproval approval, Instant at) {
        var existing = recoveries.findById(approval.approvalId());
        if (existing.isPresent()) {
            return requireSame(existing.get(), approval);
        }
        var context = approval.invocationContext();
        var snapshot =
                commands.findByTenantIdAndTaskId(
                                context.tenantId().value(), context.taskId().value())
                        .orElseThrow(() -> new IllegalStateException("审批任务缺少可恢复 Assistant 命令快照"));
        var resumeCommand = snapshot.getCommand().asResume(at);
        recoveries.schedule(
                approval.approvalId(),
                context.tenantId().value(),
                context.taskId().value(),
                JsonUtils.toJsonString(resumeCommand),
                at);
        var scheduled =
                recoveries
                        .findById(approval.approvalId())
                        .orElseThrow(() -> new IllegalStateException("恢复作业 schedule 后不可见"));
        return requireSame(scheduled, approval);
    }

    @Override
    @Transactional
    public Optional<RecoveryJob> claim(TenantId tenantId, String approvalId, Instant at) {
        var entity =
                recoveries
                        .findForUpdate(tenantId.value(), approvalId)
                        .orElseThrow(() -> new IllegalArgumentException("恢复作业不存在: " + approvalId));
        var status = Status.valueOf(entity.getStatus());
        if (status == Status.COMPLETED) {
            return Optional.empty();
        }
        if (status == Status.PROCESSING
                && entity.getLeaseUntil() != null
                && entity.getLeaseUntil().isAfter(at)) {
            return Optional.empty();
        }
        entity.setStatus(Status.PROCESSING.name());
        entity.setAttempts(entity.getAttempts() + 1);
        entity.setLeaseUntil(at.plus(RECOVERY_LEASE));
        entity.setLastError(null);
        entity.setUpdatedAt(at);
        return Optional.of(toDomain(recoveries.saveAndFlush(entity)));
    }

    @Override
    @Transactional
    public void complete(TenantId tenantId, String approvalId, Instant at) {
        var entity =
                recoveries
                        .findForUpdate(tenantId.value(), approvalId)
                        .orElseThrow(() -> new IllegalArgumentException("恢复作业不存在: " + approvalId));
        entity.setStatus(Status.COMPLETED.name());
        entity.setLeaseUntil(null);
        entity.setLastError(null);
        entity.setUpdatedAt(at);
        recoveries.saveAndFlush(entity);
    }

    @Override
    @Transactional
    public void release(TenantId tenantId, String approvalId, String failure, Instant at) {
        var entity =
                recoveries
                        .findForUpdate(tenantId.value(), approvalId)
                        .orElseThrow(() -> new IllegalArgumentException("恢复作业不存在: " + approvalId));
        if (Status.valueOf(entity.getStatus()) == Status.COMPLETED) {
            return;
        }
        entity.setStatus(Status.PENDING.name());
        entity.setLeaseUntil(null);
        entity.setLastError(truncate(failure));
        entity.setUpdatedAt(at);
        recoveries.saveAndFlush(entity);
    }

    private RecoveryJob requireSame(HitlRecoveryEntity entity, HumanApproval approval) {
        var context = approval.invocationContext();
        if (!entity.getTenantId().equals(context.tenantId().value())
                || !entity.getTaskId().equals(context.taskId().value())) {
            throw new IllegalStateException("approvalId 已绑定不同恢复任务");
        }
        return toDomain(entity);
    }

    private RecoveryJob toDomain(HitlRecoveryEntity entity) {
        return new RecoveryJob(
                entity.getApprovalId(),
                new TenantId(entity.getTenantId()),
                new TaskId(entity.getTaskId()),
                entity.getCommand(),
                Status.valueOf(entity.getStatus()),
                entity.getAttempts(),
                entity.getUpdatedAt());
    }

    private static String truncate(String failure) {
        if (failure == null || failure.isBlank()) {
            return "恢复失败";
        }
        return failure.length() <= 1000 ? failure : failure.substring(0, 1000);
    }
}
