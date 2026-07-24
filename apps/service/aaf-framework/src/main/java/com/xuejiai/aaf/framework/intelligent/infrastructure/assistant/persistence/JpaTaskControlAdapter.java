package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence;

import java.util.Objects;
import java.util.Optional;

import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantTask;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskControlPort;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;

/** PostgreSQL/JPA Assistant 任务控制适配器。 */
public final class JpaTaskControlAdapter implements TaskControlPort {

    private final AssistantTaskControlRepository repository;

    public JpaTaskControlAdapter(AssistantTaskControlRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository 不能为空");
    }

    @Override
    @Transactional
    public AssistantTask create(TenantId tenantId, AssistantTask draft) {
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
        apply(entity, draft);
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
    public AssistantTask save(TenantId tenantId, AssistantTask task) {
        Objects.requireNonNull(tenantId, "tenantId 不能为空");
        Objects.requireNonNull(task, "task 不能为空");
        var entity =
                repository
                        .findByTenantIdAndTaskId(tenantId.value(), task.taskId().value())
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Assistant 任务不存在: "
                                                        + task.taskId().value()));
        apply(entity, task);
        return repository.saveAndFlush(entity).getTask();
    }

    private static void apply(AssistantTaskControlEntity entity, AssistantTask task) {
        entity.setTaskStatus(task.status().name());
        entity.setControlMode(task.controlMode().name());
        entity.setTask(task);
    }
}
