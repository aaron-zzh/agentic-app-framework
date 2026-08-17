package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence;

import java.util.Objects;
import java.util.Optional;

import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.framework.intelligent.assistant.model.ExecutionProfileSnapshot;
import com.xuejiai.aaf.framework.intelligent.assistant.port.ExecutionProfileSnapshotPort;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;

/** executionId 级不可变执行画像存储。 */
public final class JpaExecutionProfileSnapshotAdapter implements ExecutionProfileSnapshotPort {

    private final ExecutionProfileSnapshotRepository repository;

    public JpaExecutionProfileSnapshotAdapter(ExecutionProfileSnapshotRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository 不能为空");
    }

    @Override
    @Transactional
    public ExecutionProfileSnapshot freeze(ExecutionProfileSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot 不能为空");
        var existing =
                repository.findByTenantIdAndExecutionId(
                        snapshot.tenantId().value(), snapshot.executionId().value());
        if (existing.isPresent()) {
            var frozen = existing.orElseThrow().getSnapshot();
            if (!frozen.equals(snapshot)) {
                throw new IllegalStateException("executionId 已冻结不同的 ExecutionProfileSnapshot");
            }
            return frozen;
        }
        var entity = new ExecutionProfileSnapshotEntity();
        entity.setTenantId(snapshot.tenantId().value());
        entity.setTaskId(snapshot.taskId().value());
        entity.setExecutionId(snapshot.executionId().value());
        entity.setAssistantId(snapshot.assistantId().value());
        entity.setAssistantRevision(snapshot.assistantRevision());
        entity.setSnapshot(snapshot);
        entity.setFrozenAt(snapshot.frozenAt());
        return repository.saveAndFlush(entity).getSnapshot();
    }

    @Override
    public Optional<ExecutionProfileSnapshot> find(TenantId tenantId, ExecutionId executionId) {
        Objects.requireNonNull(tenantId, "tenantId 不能为空");
        Objects.requireNonNull(executionId, "executionId 不能为空");
        return repository
                .findByTenantIdAndExecutionId(tenantId.value(), executionId.value())
                .map(ExecutionProfileSnapshotEntity::getSnapshot);
    }
}
