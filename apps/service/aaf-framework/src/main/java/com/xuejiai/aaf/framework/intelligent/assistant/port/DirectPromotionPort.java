package com.xuejiai.aaf.framework.intelligent.assistant.port;

import com.xuejiai.aaf.framework.intelligent.agent.model.InvocationContext;
import com.xuejiai.aaf.framework.intelligent.agent.model.PendingToolCall;
import com.xuejiai.aaf.framework.intelligent.assistant.model.Task;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;

/** 网关触发轻量态升级的依赖倒置端口：携带待执行调用把当前 DIRECT execution 提升为 Task。 */
public interface DirectPromotionPort {

    /** 模型显式升级工具名；网关据当前画像是否含该工具判断本次执行能否升级。 */
    String PROMOTE_TOOL_NAME = "promote_to_task";

    PromotionHandoff promoteForPendingCall(InvocationContext context, PendingToolCall call);

    record PromotionHandoff(TaskId taskId, Task.Status taskStatus) {}
}
