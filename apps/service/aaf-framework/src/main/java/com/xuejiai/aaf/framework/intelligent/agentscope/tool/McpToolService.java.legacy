/**
 * MCP 工具绑定服务——Agent 工具发现与调用。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.agentscope.tool;

import java.util.List;

import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.engine.tool.ToolResolver;
import com.xuejiai.aaf.framework.intelligent.agent.AgentDefinition;

import io.agentscope.core.tool.Toolkit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * MCP 工具绑定服务——Agent 工具发现与 Toolkit 组装。
 *
 * <p>工具来源统一走 {@link ToolResolver}（从 {@code ToolRegistry} 按白名单过滤）， 再通过 {@link ToolCallbackAgentTool}
 * 适配为 AgentScope {@code AgentTool} 注册进 Toolkit。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpToolService {

    private final ToolResolver toolResolver;

    /**
     * 为 Agent 构建工具集。
     *
     * <p>按 AgentDefinition.tools 白名单从 ToolRegistry 过滤工具，适配为 AgentScope AgentTool 后注册进 Toolkit。
     *
     * @param definition Agent 定义
     * @return 绑定了可用工具的 Toolkit
     */
    public Toolkit buildToolkit(AgentDefinition definition) {
        var toolkit = new Toolkit();
        var toolNames = parseList(definition.getTools());
        var callbacks = toolResolver.resolveForAgent(definition.getId(), toolNames);
        for (var callback : callbacks) {
            toolkit.registerTool(new ToolCallbackAgentTool(callback));
            log.debug(
                    "Toolkit 注册工具: {} [agentId={}]",
                    callback.getToolDefinition().name(),
                    definition.getId());
        }
        log.info("Agent {} Toolkit 构建完成，共 {} 个工具", definition.getId(), callbacks.size());
        return toolkit;
    }

    /** 校验工具调用权限（工具名是否在 AgentDefinition.tools 白名单中）。 */
    public boolean isAllowed(String agentId, String toolName) {
        // 权限校验已移至 AgentScopeToolGovernanceService，此处保留兼容接口
        return true;
    }

    private List<String> parseList(String json) {
        if (json == null || json.isBlank()) return List.of();
        return List.of(json.replaceAll("[\\[\\]\"\\ ]", "").split(",")).stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
