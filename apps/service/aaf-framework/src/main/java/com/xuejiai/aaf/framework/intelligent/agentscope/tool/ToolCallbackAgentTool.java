package com.xuejiai.aaf.framework.intelligent.agentscope.tool;

import java.util.Map;

import org.springframework.ai.tool.ToolCallback;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.ToolCallParam;
import reactor.core.publisher.Mono;

/**
 * ToolCallback → AgentScope AgentTool 正向适配器。
 *
 * <p>将 Spring AI {@link ToolCallback}（存储在 {@code ToolRegistry} 中）适配为 AgentScope {@link AgentTool}，
 * 使 AgentScope Agent 可以直接调用 ToolRegistry 中注册的任意工具（LOCAL / MCP / CUSTOM）。
 *
 * <p>与 {@link AgentScopeToolGovernanceService} 内的 {@code AgentToolCallback} 方向相反：
 *
 * <ul>
 *   <li>{@code AgentToolCallback}：AgentScope AgentTool → Spring AI ToolCallback（反向）
 *   <li>{@code ToolCallbackAgentTool}：Spring AI ToolCallback → AgentScope AgentTool（正向）
 * </ul>
 */
public class ToolCallbackAgentTool implements AgentTool {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final ToolCallback callback;
    private final Map<String, Object> parameters;
    private final ObjectMapper objectMapper;

    public ToolCallbackAgentTool(ToolCallback callback, ObjectMapper objectMapper) {
        this.callback = callback;
        this.objectMapper = objectMapper;
        this.parameters = parseSchema(callback.getToolDefinition().inputSchema());
    }

    @Override
    public String getName() {
        return callback.getToolDefinition().name();
    }

    @Override
    public String getDescription() {
        return callback.getToolDefinition().description();
    }

    @Override
    public Map<String, Object> getParameters() {
        return parameters;
    }

    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        return Mono.fromCallable(
                () -> {
                    var arguments =
                            param != null && param.getInput() != null
                                    ? objectMapper.writeValueAsString(param.getInput())
                                    : "{}";
                    var result = callback.call(arguments);
                    return ToolResultBlock.text(result != null ? result : "");
                });
    }

    private Map<String, Object> parseSchema(String inputSchema) {
        if (inputSchema == null || inputSchema.isBlank()) {
            return Map.of("type", "object", "properties", Map.of());
        }
        try {
            return objectMapper.readValue(inputSchema, MAP_TYPE);
        } catch (Exception e) {
            return Map.of("type", "object", "properties", Map.of());
        }
    }
}
