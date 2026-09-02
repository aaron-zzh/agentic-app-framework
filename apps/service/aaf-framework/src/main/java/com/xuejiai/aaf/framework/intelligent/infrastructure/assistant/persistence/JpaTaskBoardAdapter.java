package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence;

import java.util.Objects;
import java.util.Optional;

import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskBoard;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskBoard.ReadyClaim;
import com.xuejiai.aaf.framework.intelligent.assistant.port.ConversationLeasePort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.ConversationLeasePort.Lease;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskBoardPort;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;

/** Goal/SubTask DAG 的 PostgreSQL 聚合适配器。 */
public class JpaTaskBoardAdapter implements TaskBoardPort {
    private final TaskBoardRepository repository;
    private final ConversationLeasePort leases;

    public JpaTaskBoardAdapter(TaskBoardRepository repository, ConversationLeasePort leases) {
        this.repository = Objects.requireNonNull(repository, "repository 不能为空");
        this.leases = Objects.requireNonNull(leases, "leases 不能为空");
    }

    @Override
    @Transactional
    public TaskBoard save(TenantId tenantId, TaskBoard board) {
        var existing =
                repository
                        .findByTenantIdAndTaskId(tenantId.value(), board.taskId().value())
                        .orElse(null);
        if (existing != null
                && existing.getFencingToken() != null
                && existing.getFencingToken() > 0) {
            throw new IllegalStateException("运行期 TaskBoard 必须通过 fenced mutation 更新");
        }
        var entity = existing == null ? new TaskBoardEntity() : existing;
        entity.setTenantId(tenantId.value());
        entity.setTaskId(board.taskId().value());
        entity.setBoard(board);
        if (entity.getFencingToken() == null) entity.setFencingToken(0L);
        return repository.saveAndFlush(entity).getBoard();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TaskBoard> find(TenantId tenantId, TaskId taskId) {
        return repository
                .findByTenantIdAndTaskId(tenantId.value(), taskId.value())
                .map(TaskBoardEntity::getBoard);
    }

    @Override
    @Transactional
    public ReadyClaim claimReady(TenantId tenantId, TaskId taskId, Lease lease) {
        var entity = requireLocked(tenantId, taskId, lease);
        var claim = entity.getBoard().claimReady();
        if (!claim.subTasks().isEmpty()) {
            entity.setBoard(claim.board());
            saveFenced(entity, lease);
        }
        return claim;
    }

    @Override
    @Transactional
    public TaskBoard applyCoordinationPlan(
            TenantId tenantId,
            TaskId taskId,
            com.xuejiai.aaf.framework.intelligent.assistant.model.CoordinationPlan plan,
            Lease lease) {
        var entity = requireLocked(tenantId, taskId, lease);
        entity.setBoard(entity.getBoard().applyCoordinationPlan(plan));
        return saveFenced(entity, lease);
    }

    @Override
    @Transactional
    public TaskBoard completeSubTask(
            TenantId tenantId, TaskId taskId, String subTaskId, String result, Lease lease) {
        var entity = requireLocked(tenantId, taskId, lease);
        entity.setBoard(entity.getBoard().complete(subTaskId, result));
        return saveFenced(entity, lease);
    }

    @Override
    @Transactional
    public TaskBoard failSubTask(
            TenantId tenantId,
            TaskId taskId,
            String subTaskId,
            String failure,
            boolean transientFailure,
            Lease lease) {
        var entity = requireLocked(tenantId, taskId, lease);
        entity.setBoard(entity.getBoard().fail(subTaskId, failure, transientFailure));
        return saveFenced(entity, lease);
    }

    @Override
    @Transactional
    public TaskBoard interruptSubTask(
            TenantId tenantId, TaskId taskId, String subTaskId, boolean retryable, Lease lease) {
        var entity = requireLocked(tenantId, taskId, lease);
        entity.setBoard(entity.getBoard().interrupt(subTaskId, retryable));
        return saveFenced(entity, lease);
    }

    @Override
    @Transactional
    public TaskBoard interruptRunning(
            TenantId tenantId, TaskId taskId, boolean retryable, Lease lease) {
        var entity = requireLocked(tenantId, taskId, lease);
        entity.setBoard(entity.getBoard().interruptRunning(retryable));
        return saveFenced(entity, lease);
    }

    private TaskBoardEntity requireLocked(TenantId tenantId, TaskId taskId, Lease lease) {
        leases.requireCurrent(lease);
        if (!tenantId.equals(lease.tenantId())) {
            throw new IllegalStateException("TaskBoard tenant 与 conversation lease 不一致");
        }
        var entity =
                repository
                        .findLockedByTenantIdAndTaskId(tenantId.value(), taskId.value())
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "TaskBoard 不存在: " + taskId.value()));
        if (entity.getFencingToken() != null && entity.getFencingToken() > lease.fencingToken()) {
            throw new IllegalStateException("旧 fencing token 不能覆盖 TaskBoard");
        }
        return entity;
    }

    private TaskBoard saveFenced(TaskBoardEntity entity, Lease lease) {
        leases.requireCurrent(lease);
        entity.setFencingToken(lease.fencingToken());
        return repository.saveAndFlush(entity).getBoard();
    }
}
