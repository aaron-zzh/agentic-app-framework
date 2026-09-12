package com.xuejiai.aaf.framework.intelligent.assistant.port;

import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;

/** 仅用于唤醒调度器；信号不承载任务事实。 */
public interface TaskDispatchSignalPort {
    void signal(TenantId tenantId, TaskId taskId, String dispatchId);
}
