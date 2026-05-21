package com.xuejiai.aaf.module.examples.agentscope.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import lombok.extern.slf4j.Slf4j;

/**
 * MCP 工具集成示例配置。
 *
 * <p>演示 AgentScope 通过 MCP（Model Context Protocol）协议接入外部工具服务器。
 * MCP 是 Anthropic 提出的开放协议，允许 Agent 动态发现并调用外部工具，
 * 无需在代码中硬编码工具实现。
 *
 * <p>支持三种传输方式：
 * <ul>
 *   <li>Stdio — 本地进程（如 Python MCP Server）</li>
 *   <li>SSE — HTTP Server-Sent Events（有状态远程服务）</li>
 *   <li>StreamableHTTP — HTTP 流式（无状态远程服务）</li>
 * </ul>
 *
 * <p>启用条件：{@code aaf.examples.agentscope.enabled=true}
 * 且配置了 {@code aaf.examples.agentscope.mcp.server-url}（默认使用内置 Stdio 示例）
 */
@Slf4j
@Configuration
@ConditionalOnProperty(
        name = "aaf.examples.agentscope.enabled",
        havingValue = "true",
        matchIfMissing = false)
public class McpExampleConfig {

    /**
     * MCP Server URL。
     * 留空时无工具模式运行。
     * SSE 示例：http://localhost:3000/sse
     * Streamable HTTP 示例：https://nws.caseyjhand.co
     */
    @Value("${aaf.examples.agentscope.mcp.server-url:}")
    private String mcpServerUrl;

    /**
     * 传输类型：sse（默认）或 streamable-http。
     */
    @Value("${aaf.examples.agentscope.mcp.transport:sse}")
    private String mcpTransport;

    /**
     * MCP 工具 Agent：通过 MCP 协议动态发现并调用外部工具。
     *
     * <p>与 ② 工具调用示例的区别：
     * <ul>
     *   <li>② 工具调用：工具在 Java 代码中用 {@code @Tool} 注解定义，编译时确定</li>
     *   <li>⑦ MCP 工具：工具由外部 MCP Server 提供，运行时动态发现，无需修改代码</li>
     * </ul>
     *
     * <p>典型 MCP Server 示例：
     * <ul>
     *   <li>filesystem MCP Server — 文件读写工具</li>
     *   <li>github MCP Server — GitHub API 工具</li>
     *   <li>postgres MCP Server — 数据库查询工具</li>
     *   <li>自定义业务 MCP Server — 企业内部系统工具</li>
     * </ul>
     */
    @Bean("mcpToolAgent")
    public ReActAgent mcpToolAgent(Model exampleDashScopeModel) {
        Toolkit toolkit = new Toolkit();

        // [MCP能力点] 连接 MCP Server，自动发现并注册所有工具
        McpClientWrapper mcpClient = buildMcpClient();
        if (mcpClient != null) {
            // MCP Client 将远程工具注册到 Toolkit，Agent 可直接调用
            toolkit.registerMcpClient(mcpClient);
            log.info("MCP 工具已注册，可用工具: {}", mcpClient.listTools());
        } else {
            log.warn("MCP Server 未配置，mcpToolAgent 将以无工具模式运行。"
                    + "配置 aaf.examples.agentscope.mcp.server-url 启用 MCP 工具");
        }

        return ReActAgent.builder()
                .name("McpToolAgent")
                .sysPrompt("你是一个能使用 MCP 工具的助手。根据用户需求选择合适的工具完成任务。")
                .model(exampleDashScopeModel)
                .toolkit(toolkit)
                .memory(new InMemoryMemory())
                .build();
    }

    /**
     * 构建 MCP Client。
     *
     * <p>根据 {@code aaf.examples.agentscope.mcp.transport} 选择传输方式：
     * <ul>
     *   <li>sse（默认）— SSE 传输，适合有状态远程服务</li>
     *   <li>streamable-http — Streamable HTTP 传输，适合无状态远程服务（如 nws.caseyjhand.co）</li>
     * </ul>
     */
    private McpClientWrapper buildMcpClient() {
        if (mcpServerUrl == null || mcpServerUrl.isBlank()) {
            return null;
        }
        try {
            McpClientBuilder builder = McpClientBuilder.create("example-mcp-server");
            if ("streamable-http".equalsIgnoreCase(mcpTransport)) {
                return builder.streamableHttpTransport(mcpServerUrl).buildSync();
            }
            return builder.sseTransport(mcpServerUrl).buildSync();
        } catch (Exception e) {
            log.warn("MCP Server 连接失败 [{}]: {}", mcpServerUrl, e.getMessage());
            return null;
        }
    }
}
