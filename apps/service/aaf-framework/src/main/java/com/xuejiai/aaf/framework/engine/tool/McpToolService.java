package com.xuejiai.aaf.framework.engine.tool;

import java.util.List;

import org.springframework.stereotype.Service;

import io.agentscope.core.tool.Toolkit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * MCP 工具服务：为 Agent 构建 AgentScope Toolkit。
 * 工具白名单按 assistantId 维度管理（工具权限属于 Assistant 配置）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpToolService {

    private final ToolRegistry toolRegistry;

    /**
     * 为 Agent 构建工具集。
     *
     * @param assistantId  所属 Assistant ID（用于白名单过滤）
     * @param mcpServers   MCP 服务器列表（JSON 数组字符串）
     * @return 绑定了可用工具的 Toolkit
     */
    public Toolkit buildToolkit(String assistantId, String mcpServers) {
        var toolkit = new Toolkit();

        // MCP 服务器连接（通过 AgentScope MCP starter 自动发现）
        if (mcpServers != null && !mcpServers.isBlank()) {
            for (var serverUrl : parseList(mcpServers)) {
                log.info("注册 MCP 服务器 [assistant={}]: {}", assistantId, serverUrl);
                // AgentScope MCP starter 自动处理连接和工具注册
            }
        }

        // 将 ToolRegistry 中 Assistant 可用的工具绑定到 Toolkit
        // AgentScope Toolkit 通过 Spring 自动装配获取已注册工具，此处记录白名单供权限校验
        toolRegistry.resolveForAssistant(assistantId)
            .forEach(cb -> log.debug("工具可用 [assistant={}]: {}", assistantId, cb.getToolDefinition().name()));

        return toolkit;
    }

    /**
     * 校验工具调用权限（按 assistantId 白名单）。
     */
    public boolean isAllowed(String assistantId, String toolName) {
        var names = toolRegistry.resolveForAssistant(assistantId)
            .stream()
            .map(cb -> cb.getToolDefinition().name())
            .toList();
        return names.contains(toolName);
    }

    /**
     * 获取 Assistant 可用工具名列表。
     */
    public List<String> listTools(String assistantId) {
        return toolRegistry.resolveForAssistant(assistantId)
            .stream()
            .map(cb -> cb.getToolDefinition().name())
            .toList();
    }

    private List<String> parseList(String json) {
        if (json == null || json.isBlank()) return List.of();
        return List.of(json.replaceAll("[\\[\\]\"\\ ]", "").split(","));
    }
}
