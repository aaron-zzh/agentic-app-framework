package com.xuejiai.aaf.framework.intelligent.assistant.port;

import java.util.Optional;

import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskBoard;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskBoard.ReadyClaim;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;

/** TaskBoard/Goal/SubTask DAG 的持久化边界。 */
public interface TaskBoardPort {

    TaskBoard save(TenantId tenantId, TaskBoard board);

    Optional<TaskBoard> find(TenantId tenantId, TaskId taskId);

    ReadyClaim claimReady(TenantId tenantId, TaskId taskId, ConversationLeasePort.Lease lease);

    TaskBoard completeSubTask(
            TenantId tenantId,
            TaskId taskId,
            String subTaskId,
            String result,
            ConversationLeasePort.Lease lease);

    TaskBoard failSubTask(
            TenantId tenantId,
            TaskId taskId,
            String subTaskId,
            String failure,
            boolean transientFailure,
            ConversationLeasePort.Lease lease);

    TaskBoard interruptSubTask(
            TenantId tenantId,
            TaskId taskId,
            String subTaskId,
            boolean retryable,
            ConversationLeasePort.Lease lease);

    TaskBoard interruptRunning(
            TenantId tenantId, TaskId taskId, boolean retryable, ConversationLeasePort.Lease lease);
}
