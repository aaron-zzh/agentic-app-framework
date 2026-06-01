package com.xuejiai.aaf.framework.intelligent.agent.agentscope;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xuejiai.aaf.framework.engine.tool.ToolCallDispatcher;
import com.xuejiai.aaf.framework.engine.tool.ToolCallDispatcher.ToolCallResult;
import com.xuejiai.aaf.framework.engine.tool.ToolRegistry;
import com.xuejiai.aaf.framework.intelligent.agent.AgentDefinition;
import com.xuejiai.aaf.framework.intelligent.agent.ToolPermissionGuard;
import com.xuejiai.aaf.framework.intelligent.agent.run.AgentRunEventPublisher;
import com.xuejiai.aaf.framework.intelligent.agent.run.AgentRunEventType;

import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.ToolCallParam;
import io.agentscope.core.tool.ToolSuspendException;
import io.agentscope.core.tool.Toolkit;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

/** 将 AgentScope 的 AgentTool 包装进 AAF 统一工具治理链。 */
@Component
@RequiredArgsConstructor
public class AgentScopeToolGovernanceService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final ToolPermissionGuard toolPermissionGuard;
    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;
    private final AgentRunEventPublisher agentRunEventPublisher;

    public void apply(Toolkit toolkit, AgentDefinition definition) {
        if (toolkit == null || toolkit.getToolNames().isEmpty()) {
            return;
        }
        var allowedTools = parseList(definition.getAllowedTools());
        for (String toolName : List.copyOf(toolkit.getToolNames())) {
            var original = toolkit.getTool(toolName);
            if (original == null || original instanceof GovernedAgentTool) {
                continue;
            }
            var callback = new AgentToolCallback(original, null);
            toolRegistry.register(callback, ToolRegistry.SOURCE_MCP);
            if (toolkit.removeToolIfSame(toolName, original)) {
                toolkit.registration()
                        .agentTool(new GovernedAgentTool(original, definition, allowedTools))
                        .apply();
            }
        }
    }

    private final class GovernedAgentTool implements AgentTool {
        private final AgentTool delegate;
        private final AgentDefinition definition;
        private final List<String> allowedTools;

        private GovernedAgentTool(AgentTool delegate, AgentDefinition definition, List<String> allowedTools) {
            this.delegate = delegate;
            this.definition = definition;
            this.allowedTools = allowedTools;
        }

        @Override
        public String getName() {
            return delegate.getName();
        }

        @Override
        public String getDescription() {
            return delegate.getDescription();
        }

        @Override
        public Map<String, Object> getParameters() {
            return delegate.getParameters();
        }

        @Override
        public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
            return Mono.fromCallable(
                    () -> {
                        var context = resolveContext(param);
                        agentRunEventPublisher.publish(
                                AgentRunEventType.TOOL_CALL_STARTED,
                                "调用工具",
                                delegate.getName(),
                                Map.of(
                                        "toolName", delegate.getName(),
                                        "assistantId", context.assistantId() != null ? context.assistantId() : ""));
                        var callback = new AgentToolCallback(delegate, param);
                        var guarded =
                                toolPermissionGuard
                                        .guard(
                                                List.of(callback),
                                                context.sessionId(),
                                                context.assistantId(),
                                                allowedTools)
                                        .getFirst();
                        String output;
                        try {
                            output = guarded.call(arguments(param));
                        } catch (RuntimeException ex) {
                            agentRunEventPublisher.publish(
                                    AgentRunEventType.TOOL_CALL_FAILED,
                                    "工具调用失败",
                                    delegate.getName(),
                                    Map.of(
                                            "toolName",
                                            delegate.getName(),
                                            "error",
                                            ex.getMessage() != null ? ex.getMessage() : ""));
                            throw ex;
                        }
                        if (shouldSuspend(output)) {
                            throw new ToolSuspendException(output);
                        }
                        agentRunEventPublisher.publish(
                                AgentRunEventType.TOOL_CALL_COMPLETED,
                                "工具调用完成",
                                delegate.getName(),
                                Map.of("toolName", delegate.getName()));
                        return ToolResultBlock.text(output);
                    });
        }

        private AafAgentScopeContext resolveContext(ToolCallParam param) {
            if (param != null && param.getContext() != null) {
                var context = param.getContext().get(AafAgentScopeContext.class);
                if (context != null) {
                    return context;
                }
            }
            var assistantId = definition.getAgentId();
            return new AafAgentScopeContext("agent:" + assistantId, assistantId);
        }
    }

    private final class AgentToolCallback implements ToolCallback {
        private final AgentTool delegate;
        private final ToolCallParam originalParam;
        private final ToolDefinition definition;

        private AgentToolCallback(AgentTool delegate, ToolCallParam originalParam) {
            this.delegate = delegate;
            this.originalParam = originalParam;
            this.definition =
                    DefaultToolDefinition.builder()
                            .name(delegate.getName())
                            .description(delegate.getDescription())
                            .inputSchema(inputSchema(delegate))
                            .build();
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return definition;
        }

        @Override
        public String call(String arguments) {
            try {
                var input = parseArguments(arguments);
                var toolUse =
                        originalParam != null && originalParam.getToolUseBlock() != null
                                ? originalParam.getToolUseBlock()
                                : ToolUseBlock.builder()
                                        .id(UUID.randomUUID().toString())
                                        .name(delegate.getName())
                                        .input(input)
                                        .build();
                var param =
                        ToolCallParam.builder()
                                .toolUseBlock(toolUse)
                                .input(input)
                                .agent(originalParam == null ? null : originalParam.getAgent())
                                .context(originalParam == null ? null : originalParam.getContext())
                                .emitter(originalParam == null ? null : originalParam.getEmitter())
                                .build();
                var result = delegate.callAsync(param).block();
                if (result != null && result.isSuspended()) {
                    throw new ToolSuspendException(resultText(result));
                }
                var output = resultText(result);
                if (output.startsWith("Error:")) {
                    return objectMapper.writeValueAsString(
                            ToolCallResult.error(delegate.getName(), "AGENTSCOPE_TOOL_ERROR", output));
                }
                return objectMapper.writeValueAsString(
                        ToolCallResult.success(delegate.getName(), objectMapper.writeValueAsString(result)));
            } catch (ToolSuspendException ex) {
                throw ex;
            } catch (Exception ex) {
                return "{\"success\":false,\"code\":\"AGENTSCOPE_TOOL_ERROR\",\"message\":\""
                        + escape(ex.getMessage())
                        + "\"}";
            }
        }
    }

    private String inputSchema(AgentTool tool) {
        try {
            return objectMapper.writeValueAsString(tool.getParameters());
        } catch (Exception ex) {
            return "{\"type\":\"object\"}";
        }
    }

    private String arguments(ToolCallParam param) throws com.fasterxml.jackson.core.JsonProcessingException {
        return objectMapper.writeValueAsString(param == null ? Map.of() : param.getInput());
    }

    private Map<String, Object> parseArguments(String arguments) throws com.fasterxml.jackson.core.JsonProcessingException {
        if (arguments == null || arguments.isBlank()) {
            return Map.of();
        }
        return objectMapper.readValue(arguments, MAP_TYPE);
    }

    private boolean shouldSuspend(String output) {
        if (output == null || output.isBlank()) {
            return false;
        }
        try {
            var result = objectMapper.readValue(output, ToolCallDispatcher.ToolCallResult.class);
            return !result.success()
                    && result.recoverable()
                    && (result.pendingApproval() || result.resume() != null);
        } catch (Exception ignored) {
            return false;
        }
    }

    private String resultText(ToolResultBlock result) {
        if (result == null || result.getOutput().isEmpty()) {
            return "";
        }
        return result.getOutput().stream()
                .map(block -> block instanceof TextBlock text ? text.getText() : block.toString())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    private List<String> parseList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        return List.of(json.replaceAll("[\\[\\]\"\\ ]", "").split(",")).stream()
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
