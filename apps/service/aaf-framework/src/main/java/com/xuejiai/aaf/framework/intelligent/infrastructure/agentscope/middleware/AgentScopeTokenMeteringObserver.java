package com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.middleware;

import java.time.Instant;
import java.util.Objects;

import com.xuejiai.aaf.framework.intelligent.agent.model.AgentExecutionCommand;
import com.xuejiai.aaf.framework.intelligent.agent.port.TokenMeteringPort;
import com.xuejiai.aaf.framework.intelligent.agent.port.TokenMeteringPort.ModelUsageFact;
import com.xuejiai.aaf.framework.intelligent.assistant.port.DelegatedTaskPort;
import com.xuejiai.aaf.framework.intelligent.core.model.ModelSpec;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ControlMode;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.event.ModelCallEndEvent;

/** AgentScope 模型调用预算与真实计量观察器。 */
public final class AgentScopeTokenMeteringObserver {
    private final TokenMeteringPort metering;
    private final DelegatedTaskPort delegatedTasks;

    public AgentScopeTokenMeteringObserver(
            TokenMeteringPort metering, DelegatedTaskPort delegatedTasks) {
        this.metering = Objects.requireNonNull(metering, "metering 不能为空");
        this.delegatedTasks = Objects.requireNonNull(delegatedTasks, "delegatedTasks 不能为空");
    }

    public void observe(AgentEvent event, ModelSpec model, AgentExecutionCommand command) {
        if (event.getType() == AgentEventType.MODEL_CALL_START
                && command.context().controlMode() == ControlMode.DELEGATED) {
            delegatedTasks.reserveModelCall(command.context(), Instant.now());
            return;
        }
        if (event.getType() != AgentEventType.MODEL_CALL_END) return;
        var completed = (ModelCallEndEvent) event;
        var usage = Objects.requireNonNull(completed.getUsage(), "模型结束事件缺少 usage");
        var usageId = Objects.requireNonNull(completed.getId(), "模型结束事件缺少 eventId");
        metering.record(new ModelUsageFact(
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
