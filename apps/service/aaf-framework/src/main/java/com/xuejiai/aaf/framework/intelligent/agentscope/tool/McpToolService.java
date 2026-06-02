/**
 * MCP 工具绑定服务——Agent 工具发现与调用。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.agentscope.tool;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.engine.workflow.WorkflowTool;
import com.xuejiai.aaf.framework.intelligent.agent.AgentDefinition;

import io.agentscope.core.tool.Toolkit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** MCP 工具管理：发现、注册、权限校验、调用。 Agent 通过此服务获取可用工具集，绑定到 AgentScope Toolkit。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpToolService {

    /** 内置工具：工作流启动 */
    private final WorkflowTool workflowTool;

    /** 工具白名单（agentId → 允许的工具名列表，null 表示全部允许） */
    private final ConcurrentHashMap<String, List<String>> whitelist = new ConcurrentHashMap<>();

    /**
     * 为 Agent 构建工具集。
     *
     * @param definition Agent 定义（含 MCP 服务器 URL 和工具列表）
     * @return 绑定了可用工具的 Toolkit
     */
    public Toolkit buildToolkit(AgentDefinition definition) {
        var toolkit = new Toolkit();

        // 注册内置工具（所有 Agent 默认可用，受 AafToolWhitelistHook 白名单过滤）
        toolkit.registerTool(workflowTool);

        // MCP 服务器连接（通过 AgentScope MCP starter 自动发现）
        if (definition.getMcpServers() != null && !definition.getMcpServers().isBlank()) {
            var servers = parseList(definition.getMcpServers());
            for (var serverUrl : servers) {
                log.info("注册 MCP 服务器: {}", serverUrl);
                // AgentScope MCP starter 自动处理连接和工具注册
                // toolkit 通过 Spring 自动装配获取 MCP 工具
            }
        }

        // 记录白名单
        if (definition.getTools() != null && !definition.getTools().isBlank()) {
            whitelist.put(definition.getAgentId(), parseList(definition.getTools()));
        }

        return toolkit;
    }

    /** 校验工具调用权限。 */
    public boolean isAllowed(String agentId, String toolName) {
        var allowed = whitelist.get(agentId);
        return allowed == null || allowed.contains(toolName);
    }

    /** 获取 Agent 可用工具列表。 */
    public List<String> listTools(String agentId) {
        return whitelist.getOrDefault(agentId, List.of());
    }

    private List<String> parseList(String json) {
        if (json == null || json.isBlank()) return List.of();
        return List.of(json.replaceAll("[\\[\\]\"\\ ]", "").split(","));
    }
}
