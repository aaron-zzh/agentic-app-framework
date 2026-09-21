package com.xuejiai.aaf.framework.intelligent.assistant.port;

import java.util.List;
import java.util.Optional;

import com.xuejiai.aaf.framework.intelligent.assistant.model.Task;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskDetails;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ConversationId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;

/** canonical Task/Plan/Execution/Dispatch 只读投影边界。 */
public interface TaskQueryPort {
    Optional<TaskDetails> find(TenantId tenantId, UserId userId, TaskId taskId);

    List<TaskDetails> list(TenantId tenantId, UserId userId, ConversationId conversationId);

    /**
     * 统计该归属者未终结且由指定来源创建的 canonical Task 数量。
     *
     * <p>供调用方做并发准入，只返回计数、不暴露任务明细；终态判定取自 {@link Task.Status#terminal()}，本端口不另建终态集合。
     */
    long countActiveBySource(TenantId tenantId, UserId userId, Task.Source source);
}
