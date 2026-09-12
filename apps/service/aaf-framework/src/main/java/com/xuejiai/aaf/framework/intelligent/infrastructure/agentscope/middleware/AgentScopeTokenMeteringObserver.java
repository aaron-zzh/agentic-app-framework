package com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.middleware;

import java.time.Instant;
import java.util.Objects;

import com.xuejiai.aaf.framework.intelligent.agent.model.AgentExecutionCommand;
import com.xuejiai.aaf.framework.intelligent.agent.port.TokenMeteringPort;
import com.xuejiai.aaf.framework.intelligent.agent.port.TokenMeteringPort.ModelUsageFact;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskUnitOfWork;
import com.xuejiai.aaf.framework.intelligent.core.model.ModelSpec;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ControlMode;

import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.event.ModelCallEndEvent;

/**
 * AgentScope 模型调用预算与真实计量观察器。
 *
 * <p>调用前（MODEL_CALL_START）预扣委托任务预算，调用后（MODEL_CALL_END）按真实 usage 记账。 用 AgentScope 事件 id
 * 作为计量幂等键，重复投递不会重复计费。
 */
public final class AgentScopeTokenMeteringObserver {
    private final TokenMeteringPort metering;
    private final TaskUnitOfWork delegatedTasks;

    public AgentScopeTokenMeteringObserver(
            TokenMeteringPort metering, TaskUnitOfWork delegatedTasks) {
        this.metering = Objects.requireNonNull(metering, "metering 不能为空");
        this.delegatedTasks = Objects.requireNonNull(delegatedTasks, "delegatedTasks 不能为空");
    }

    /** 只关心模型调用的开始与结束两类事件，其余直接忽略。 */
    public void observe(AgentEvent event, ModelSpec model, AgentExecutionCommand command) {
        if (event.getType() == AgentEventType.MODEL_CALL_START
                && command.context().controlMode() == ControlMode.DELEGATED) {
            // 仅委托态需要预扣额度：无人值守执行必须先占预算再调模型
            delegatedTasks.reserveModelCall(command.context(), Instant.now());
            return;
        }
        if (event.getType() != AgentEventType.MODEL_CALL_END) return;
        var completed = (ModelCallEndEvent) event;
        // usage / eventId 缺失说明上游契约被破坏，直接失败而非静默漏账
        var usage = Objects.requireNonNull(completed.getUsage(), "模型结束事件缺少 usage");
        var usageId = Objects.requireNonNull(completed.getId(), "模型结束事件缺少 eventId");
        metering.record(
                new ModelUsageFact(
                        usageId,
                        command.context(),
                        model.modelId(),
                        "CHAT",
                        usage.getInputTokens(),
                        usage.getOutputTokens(),
                        usage.getCachedTokens(),
                        Instant.parse(completed.getCreatedAt())));
    }
}
