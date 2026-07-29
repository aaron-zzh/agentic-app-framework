package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence;

import java.util.Objects;
import java.util.Optional;

import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantTask;
import com.xuejiai.aaf.framework.intelligent.assistant.port.ConversationLeasePort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.ConversationLeasePort.Lease;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskControlPort;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;

/** PostgreSQL/JPA Assistant 任务控制适配器。 */
public final class JpaTaskControlAdapter implements TaskControlPort {

    private final AssistantTaskControlRepository repository;
    private final ConversationLeasePort leases;

    public JpaTaskControlAdapter(
            AssistantTaskControlRepository repository, ConversationLeasePort leases) {
        this.repository = Objects.requireNonNull(repository, "repository 不能为空");
        this.leases = Objects.requireNonNull(leases, "leases 不能为空");
    }

    @Override
    @Transactional
    public AssistantTask create(TenantId tenantId, AssistantTask draft, Lease lease) {
        requireLease(tenantId, draft, lease);
        Objects.requireNonNull(tenantId, "tenantId 不能为空");
        Objects.requireNonNull(draft, "draft 不能为空");
        if (repository
                .findByTenantIdAndTaskId(tenantId.value(), draft.taskId().value())
                .isPresent()) {
            throw new IllegalStateException("Assistant 任务已存在: " + draft.taskId().value());
        }
        var entity = new AssistantTaskControlEntity();
        entity.setTenantId(tenantId.value());
        entity.setTaskId(draft.taskId().value());
        apply(entity, draft, lease);
        return repository.saveAndFlush(entity).getTask();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AssistantTask> find(TenantId tenantId, TaskId taskId) {
        Objects.requireNonNull(tenantId, "tenantId 不能为空");
        Objects.requireNonNull(taskId, "taskId 不能为空");
        return repository
                .findByTenantIdAndTaskId(tenantId.value(), taskId.value())
                .map(AssistantTaskControlEntity::getTask);
    }

    @Override
    @Transactional
    public AssistantTask save(TenantId tenantId, AssistantTask task, Lease lease) {
        requireLease(tenantId, task, lease);
        Objects.requireNonNull(tenantId, "tenantId 不能为空");
        Objects.requireNonNull(task, "task 不能为空");
        var entity =
                repository
                        .findByTenantIdAndTaskId(tenantId.value(), task.taskId().value())
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Assistant 任务不存在: " + task.taskId().value()));
        if (lease != null && entity.getFencingToken() > lease.fencingToken()) {
            throw new IllegalStateException("旧 fencing token 不能覆盖 TaskControl");
        }
        apply(entity, task, lease);
        return repository.saveAndFlush(entity).getTask();
    }

    private void requireLease(TenantId tenantId, AssistantTask task, Lease lease) {
        if (task.controlMode()
                != com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ControlMode
                        .DELEGATED) {
            return;
        }
        if (lease == null || !tenantId.equals(lease.tenantId())) {
            throw new IllegalStateException("DELEGATED 任务状态写入缺少匹配 lease");
        }
        leases.requireCurrent(lease);
    }

    private static void apply(AssistantTaskControlEntity entity, AssistantTask task, Lease lease) {
        entity.setTaskStatus(task.status().name());
        entity.setControlMode(task.controlMode().name());
        entity.setTask(task);
        entity.setFencingToken(lease == null ? 0L : lease.fencingToken());
    }
}
