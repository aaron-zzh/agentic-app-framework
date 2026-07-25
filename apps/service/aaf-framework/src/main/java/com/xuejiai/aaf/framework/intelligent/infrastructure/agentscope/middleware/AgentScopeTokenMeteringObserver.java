package com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.middleware;

import java.time.Instant;
import java.util.Objects;

import com.xuejiai.aaf.framework.intelligent.agent.model.AgentExecutionCommand;
import com.xuejiai.aaf.framework.intelligent.agent.model.AgentSpec;
import com.xuejiai.aaf.framework.intelligent.agent.port.TokenMeteringPort;
import com.xuejiai.aaf.framework.intelligent.agent.port.TokenMeteringPort.ModelUsageFact;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.event.ModelCallEndEvent;

/** AgentScope 模型结束事件观察器；缺 usage 或模型映射时明确失败。 */
public final class AgentScopeTokenMeteringObserver {
    private final TokenMeteringPort metering;

    public AgentScopeTokenMeteringObserver(TokenMeteringPort metering) {
        this.metering = Objects.requireNonNull(metering, "metering 不能为空");
    }

    public void observe(AgentEvent event, AgentSpec spec, AgentExecutionCommand command) {
        if (event.getType() != AgentEventType.MODEL_CALL_END) return;
        var completed = (ModelCallEndEvent) event;
        var usage = Objects.requireNonNull(completed.getUsage(), "模型结束事件缺少 usage");
        var usageId = Objects.requireNonNull(completed.getId(), "模型结束事件缺少 eventId");
        metering.record(new ModelUsageFact(
                usageId,
                command.context().tenantId(),
                command.context().userId(),
                command.context().taskId(),
                command.context().executionId(),
                spec.model().modelId(),
                "CHAT",
                usage.getInputTokens(),
                usage.getOutputTokens(),
                usage.getCachedTokens(),
                Instant.parse(completed.getCreatedAt())));
    }
}
