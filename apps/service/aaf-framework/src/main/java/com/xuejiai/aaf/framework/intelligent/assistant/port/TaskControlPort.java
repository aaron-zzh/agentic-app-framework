package com.xuejiai.aaf.framework.intelligent.assistant.port;

import java.util.Optional;

import com.xuejiai.aaf.framework.intelligent.assistant.port.ConversationLeasePort.Lease;
import com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantTask;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;

/** Assistant 任务生命周期、责任主体与恢复点的持久化边界。 */
public interface TaskControlPort {

    AssistantTask create(TenantId tenantId, AssistantTask draft, Lease lease);

    Optional<AssistantTask> find(TenantId tenantId, TaskId taskId);

    AssistantTask save(TenantId tenantId, AssistantTask task, Lease lease);
}
