package com.xuejiai.aaf.framework.intelligent.assistant.port;

import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;

/** approvalId 幂等恢复调度入口；可由即时事件或重启后的显式 API 调用。 */
public interface TaskRecoveryDispatchPort {

    boolean recover(TenantId tenantId, String approvalId);
}
