package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence;

import java.util.Objects;
import java.util.Optional;

import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.framework.intelligent.assistant.model.ExecutionProfileSnapshot;
import com.xuejiai.aaf.framework.intelligent.assistant.port.ExecutionProfileSnapshotPort;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;

/** executionId 级追加式画像存储：每次物理调用推进的画像追加一条新记录，恢复取最新一条。 */
public class JpaExecutionProfileSnapshotAdapter implements ExecutionProfileSnapshotPort {

    private final ExecutionProfileSnapshotRepository repository;

    public JpaExecutionProfileSnapshotAdapter(ExecutionProfileSnapshotRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository 不能为空");
    }

    @Override
    @Transactional
    public ExecutionProfileSnapshot freeze(ExecutionProfileSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot 不能为空");
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
                .findFirstByTenantIdAndExecutionIdOrderByFrozenAtDesc(
                        tenantId.value(), executionId.value())
                .map(ExecutionProfileSnapshotEntity::getSnapshot);
    }
}
