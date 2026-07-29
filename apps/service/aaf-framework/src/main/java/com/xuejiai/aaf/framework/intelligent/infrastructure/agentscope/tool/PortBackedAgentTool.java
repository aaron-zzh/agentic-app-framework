package com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.tool;

import java.util.Map;

import com.xuejiai.aaf.framework.intelligent.agent.model.InvocationContext;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolCatalogPort.ToolDefinition;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolGatewayPort;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolGatewayPort.ApprovalRequiredException;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort.ToolInvocation;

import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.ToolBase;
import io.agentscope.core.tool.ToolCallParam;
import io.agentscope.core.tool.ToolSuspendException;
import reactor.core.publisher.Mono;

/** AgentScope 最终工具边界；所有真实调用必须经过 AAF ToolGateway。 */
final class PortBackedAgentTool extends ToolBase {

    private final ToolDefinition definition;
    private final ToolGatewayPort gateway;
    private final ToolResultEvidenceStore evidenceStore;

    PortBackedAgentTool(
            ToolDefinition definition,
            ToolGatewayPort gateway,
            ToolResultEvidenceStore evidenceStore) {
        super(
                ToolBase.builder()
                        .name(definition.ref().name())
                        .description(definition.description())
                        .inputSchema(definition.inputSchema())
                        .readOnly(definition.readOnly())
                        .concurrencySafe(true));
        this.definition = definition;
        this.gateway = gateway;
        this.evidenceStore = evidenceStore;
    }

    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        var runtimeContext = param.getRuntimeContext();
        var context = runtimeContext == null ? null : runtimeContext.get(InvocationContext.class);
        if (context == null)
            return Mono.error(new IllegalStateException("工具调用缺少 typed InvocationContext"));
        var toolUse = param.getToolUseBlock();
        if (toolUse == null || toolUse.getId() == null) {
            return Mono.error(new IllegalStateException("工具调用缺少 toolCallId"));
        }
        var invocation =
                new ToolInvocation(toolUse.getId(), definition.ref(), param.getInput(), context);
        return gateway.invoke(definition, invocation)
                .map(
                        result -> {
                            evidenceStore.record(
                                    context.executionId(),
                                    toolUse.getId(),
                                    result.metadata(),
                                    definition.reversible(),
                                    definition.requireConfirm());
                            return ToolResultBlock.of(
                                    TextBlock.builder().text(result.output()).build(),
                                    result.metadata());
                        })
                .onErrorMap(
                        ApprovalRequiredException.class,
                        failure -> {
                            evidenceStore.record(
                                    context.executionId(),
                                    toolUse.getId(),
                                    Map.of("approvalId", failure.approvalId()),
                                    definition.reversible(),
                                    true);
                            return new ToolSuspendException(failure.getMessage());
                        });
    }
}
