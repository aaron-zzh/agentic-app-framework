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
     * 统计该归属者占用并发名额且由指定来源创建的 canonical Task 数量。
     *
     * <p>供调用方做并发准入，只返回计数、不暴露任务明细。稳定 {@link Task.Status#PAUSED} 已无在飞执行并释放名额，但仍保持可恢复的非终态； {@code
     * PAUSING} 在完成暂停前继续占用名额。
     */
    long countActiveBySource(TenantId tenantId, UserId userId, Task.Source source);
}
