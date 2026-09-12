package com.xuejiai.aaf.framework.intelligent.assistant.port;

import java.util.List;
import java.util.Optional;

import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskDetails;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ConversationId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;

/** canonical Task/Plan/Execution/Dispatch 只读投影边界。 */
public interface TaskQueryPort {
    Optional<TaskDetails> find(TenantId tenantId, UserId userId, TaskId taskId);

    List<TaskDetails> list(TenantId tenantId, UserId userId, ConversationId conversationId);
}
