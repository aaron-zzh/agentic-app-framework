package com.xuejiai.aaf.framework.intelligent.assistant.port;

import java.util.Optional;

import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskPlan;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;

/** TaskPlan/Goal/TaskNode DAG 的持久化边界。 */
public interface TaskPlanPort {

    Optional<TaskPlan> find(TenantId tenantId, TaskId taskId);

    /** 仅更新既有 frozen plan 的节点状态；禁止通过此入口创建或替换 graph。 */
    TaskPlan update(TenantId tenantId, TaskPlan plan, ConversationLeasePort.Lease lease);

    TaskPlan applyTaskPlanDraft(
            TenantId tenantId,
            TaskId taskId,
            com.xuejiai.aaf.framework.intelligent.assistant.model.TaskPlanDraft plan,
            ConversationLeasePort.Lease lease);

    TaskPlan completeNode(
            TenantId tenantId,
            TaskId taskId,
            String nodeId,
            String result,
            ConversationLeasePort.Lease lease);

    TaskPlan failNode(
            TenantId tenantId,
            TaskId taskId,
            String nodeId,
            String failure,
            boolean transientFailure,
            ConversationLeasePort.Lease lease);

    TaskPlan interruptNode(
            TenantId tenantId,
            TaskId taskId,
            String nodeId,
            boolean retryable,
            ConversationLeasePort.Lease lease);

    TaskPlan interruptRunning(
            TenantId tenantId, TaskId taskId, boolean retryable, ConversationLeasePort.Lease lease);
}
