package com.xuejiai.aaf.framework.engine.tool.mcp;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.engine.tool.ConnectorToolCallback;
import com.xuejiai.aaf.framework.engine.tool.ToolRegistry;

import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.agentscope.core.tool.mcp.McpTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * MCP 动态连接服务——连接外部 MCP Server，拉取工具列表，注册进 ToolRegistry。
 *
 * <p>连接断开时自动注销对应工具，保持 ToolRegistry 与实际可用工具一致。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpConnectionService {

    private final ToolRegistry toolRegistry;

    /** serverName → McpClientWrapper */
    private final Map<String, McpClientWrapper> activeClients = new ConcurrentHashMap<>();

    /**
     * 连接 MCP Server 并将其工具注册进 ToolRegistry。
     *
     * @param serverName 唯一名称（用于注销时定位）
     * @param url 服务地址
     * @param transport 传输协议：HTTP / SSE / STDIO
     */
    public void connect(String serverName, String url, String transport) {
        disconnect(serverName); // 幂等：先断开旧连接

        McpClientWrapper client;
        try {
            var builder = McpClientBuilder.create(serverName);
            client =
                    switch (transport.toUpperCase()) {
                        case "SSE" -> builder.sseTransport(url).buildAsync().block();
                        default -> builder.streamableHttpTransport(url).buildAsync().block();
                    };
            if (client == null) throw new IllegalStateException("MCP Client 创建失败");
            client.initialize().block();
        } catch (Exception e) {
            log.error(
                    "连接 MCP Server 失败: name={}, url={}, error={}", serverName, url, e.getMessage());
            throw new RuntimeException("MCP Server 连接失败: " + e.getMessage(), e);
        }

        // 拉取工具列表，适配为 ToolCallback 注册进 ToolRegistry
        var tools = client.listTools().block();
        if (tools != null) {
            for (var tool : tools) {
                var params = McpTool.convertMcpSchemaToParameters(tool.inputSchema(), null);
                var mcpTool = new McpTool(tool.name(), tool.description(), params, client);
                var callback = new McpToolCallback(mcpTool, client);
                toolRegistry.register(callback, ToolRegistry.SOURCE_MCP);
            }
            log.info("MCP Server [{}] 注册 {} 个工具", serverName, tools.size());
        }

        activeClients.put(serverName, client);
    }

    /** 断开 MCP Server 并注销其工具。 */
    public void disconnect(String serverName) {
        var client = activeClients.remove(serverName);
        if (client == null) return;
        // 注销该 server 下注册的工具（来源标记为 SOURCE_MCP + serverName 前缀）
        toolRegistry.listBySource(ToolRegistry.SOURCE_MCP).stream()
                .filter(
                        m ->
                                m.description() != null
                                        && m.description().contains("[" + serverName + "]"))
                .map(m -> m.name())
                .toList()
                .forEach(toolRegistry::unregister);
        try {
            client.close();
        } catch (Exception ignored) {
        }
        log.info("MCP Server [{}] 已断开", serverName);
    }

    /** 应用启动时重连所有已持久化的 enabled MCP Server。 */
    public void reconnectAll(java.util.List<McpServerConfig> servers) {
        for (var s : servers) {
            try {
                connect(s.name(), s.url(), s.transport());
            } catch (Exception e) {
                log.warn("启动重连 MCP Server [{}] 失败: {}", s.name(), e.getMessage());
            }
        }
    }

    /** MCP Server 连接配置（供 reconnectAll 使用）。 */
    public record McpServerConfig(String name, String url, String transport) {}

    // ── 内部 ToolCallback 适配器 ─────────────────────────────────────────────

    /** 将 AgentScope McpTool 适配为受信 Connector 回调。 */
    private static final class McpToolCallback implements ConnectorToolCallback {

        private final McpTool mcpTool;
        private final McpClientWrapper client;
        private final org.springframework.ai.tool.definition.ToolDefinition definition;

        McpToolCallback(McpTool mcpTool, McpClientWrapper client) {
            this.mcpTool = mcpTool;
            this.client = client;
            this.definition =
                    DefaultToolDefinition.builder()
                            .name(mcpTool.getName())
                            .description(mcpTool.getDescription())
                            .inputSchema(schemaToString(mcpTool.getParameters()))
                            .build();
        }

        @Override
        public org.springframework.ai.tool.definition.ToolDefinition getToolDefinition() {
            return definition;
        }

        @Override
        public String callConnector(
                Map<String, Object> businessArguments,
                String vaultRef,
                String idempotencyKey) {
            try {
                var input = new LinkedHashMap<String, Object>(businessArguments);
                var result = client.callTool(
                                mcpTool.getName(),
                                input,
                                Map.of("vaultRef", vaultRef, "idempotencyKey", idempotencyKey))
                        .block();
                if (result == null) {
                    throw new IllegalStateException("MCP Connector 返回空结果");
                }
                if (Boolean.TRUE.equals(result.isError())) {
                    throw new IllegalStateException("MCP Connector 返回错误: " + result.content());
                }
                return io.agentscope.core.tool.mcp.McpContentConverter.convertCallToolResult(result)
                        .toString();
            } catch (Exception failure) {
                throw new IllegalStateException("MCP Connector 调用失败", failure);
            }
        }

        private static String schemaToString(Map<String, Object> schema) {
            return JsonUtils.toJsonString(schema);
        }
    }
}
