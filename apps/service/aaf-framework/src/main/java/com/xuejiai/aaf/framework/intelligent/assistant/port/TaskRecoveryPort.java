package com.xuejiai.aaf.framework.intelligent.assistant.port;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import com.xuejiai.aaf.framework.intelligent.assistant.application.AssistantCommand;
import com.xuejiai.aaf.framework.intelligent.assistant.model.HumanApproval;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;

/** Assistant 命令快照与 approvalId 恢复作业的持久边界。 */
public interface TaskRecoveryPort {

    void saveCommand(AssistantCommand command);

    RecoveryJob schedule(HumanApproval approval, Instant at);

    Optional<RecoveryJob> claim(TenantId tenantId, String approvalId, Instant at);

    void complete(TenantId tenantId, String approvalId, Instant at);

    void release(TenantId tenantId, String approvalId, String failure, Instant at);

    record RecoveryJob(
            String approvalId,
            TenantId tenantId,
            TaskId taskId,
            AssistantCommand command,
            Status status,
            int attempts,
            Instant updatedAt) {
        public RecoveryJob {
            Objects.requireNonNull(approvalId, "approvalId 不能为空");
            Objects.requireNonNull(tenantId, "tenantId 不能为空");
            Objects.requireNonNull(taskId, "taskId 不能为空");
            Objects.requireNonNull(command, "command 不能为空");
            Objects.requireNonNull(status, "status 不能为空");
            Objects.requireNonNull(updatedAt, "updatedAt 不能为空");
        }
    }

    enum Status {
        PENDING,
        PROCESSING,
        COMPLETED
    }
}
