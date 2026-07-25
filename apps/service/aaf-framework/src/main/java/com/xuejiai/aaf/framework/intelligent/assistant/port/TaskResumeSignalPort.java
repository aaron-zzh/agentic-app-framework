package com.xuejiai.aaf.framework.intelligent.assistant.port;

import java.time.Instant;

import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;

/** 外部决定完成后的非轮询恢复信号。 */
public interface TaskResumeSignalPort {

    void publish(ResumeSignal signal);

    record ResumeSignal(
            TenantId tenantId,
            TaskId taskId,
            ExecutionId executionId,
            String approvalId,
            boolean approved,
            Instant decidedAt) {}
}
