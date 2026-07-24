package com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.tool;

import java.util.Map;

import com.xuejiai.aaf.framework.intelligent.agent.model.InvocationContext;
import com.xuejiai.aaf.framework.intelligent.agent.model.ToolAuthorizationContext.AuthorizationDecision;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolCatalogPort.ToolDefinition;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort.ToolInvocation;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.ToolBase;
import io.agentscope.core.tool.ToolCallParam;
import io.agentscope.core.tool.ToolSuspendException;
import reactor.core.publisher.Mono;

/** 仅负责将 AgentScope 工具调用转交 AAF 工具治理端口。 */
final class PortBackedAgentTool extends ToolBase {

    private final ToolDefinition definition;
    private final ToolInvocationPort invocationPort;
    private final ToolResultEvidenceStore evidenceStore;

    PortBackedAgentTool(
            ToolDefinition definition,
            ToolInvocationPort invocationPort,
            ToolResultEvidenceStore evidenceStore) {
        super(
                ToolBase.builder()
                        .name(definition.ref().name())
                        .description(definition.description())
                        .inputSchema(definition.inputSchema())
                        .readOnly(definition.readOnly())
                        .concurrencySafe(true));
        this.definition = definition;
        this.invocationPort = invocationPort;
        this.evidenceStore = evidenceStore;
    }

    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        var runtimeContext = param.getRuntimeContext();
        var invocationContext =
                runtimeContext == null ? null : runtimeContext.get(InvocationContext.class);
        if (invocationContext == null) {
            return Mono.error(new IllegalStateException("工具调用缺少 typed InvocationContext"));
        }
        var toolUse = param.getToolUseBlock();
        if (toolUse == null || toolUse.getId() == null) {
            return Mono.error(new IllegalStateException("工具调用缺少 toolCallId"));
        }
        var toolKey = definition.ref().name();
        var authorization = invocationContext.toolAuthorization();
        var rule = authorization.rules().get(toolKey);
        var decision =
                authorization.evaluate(
                        invocationContext.controlMode(),
                        toolKey,
                        definition.readOnly(),
                        definition.reversible());
        if (decision == AuthorizationDecision.AUTHORIZATION_REQUIRED) {
            evidenceStore.record(
                    invocationContext.executionId(),
                    toolUse.getId(),
                    Map.of(),
                    rule.reversible(),
                    true);
            return Mono.error(new ToolSuspendException("工具写操作需要用户授权: " + toolKey));
        }

        var invocation =
                new ToolInvocation(
                        toolUse.getId(), definition.ref(), param.getInput(), invocationContext);
        return invocationPort
                .invoke(invocation)
                .map(
                        result -> {
                            evidenceStore.record(
                                    invocationContext.executionId(),
                                    toolUse.getId(),
                                    result.metadata(),
                                    rule.reversible(),
                                    rule.authorizationRequired());
                            return ToolResultBlock.of(
                                    TextBlock.builder().text(result.output()).build(),
                                    result.metadata());
                        });
    }
}
