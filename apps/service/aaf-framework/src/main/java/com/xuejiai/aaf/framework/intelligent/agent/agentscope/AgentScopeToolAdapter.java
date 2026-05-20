package com.xuejiai.aaf.framework.intelligent.agent.agentscope;

import java.util.List;

import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.ToolRegistry;
import io.agentscope.core.tool.RegisteredToolFunction;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * AgentScope Toolkit/ToolRegistry → AAF 工具引擎适配器。
 *
 * <p>适配策略：委托给 AgentScope {@link Toolkit} 和 {@link ToolRegistry}，
 * 替换 AAF 自研的 {@code ToolRegistry} 和 {@code ToolCallDispatcher}。
 *
 * <p>AgentScope 工具体系：
 * <ul>
 *   <li>{@link Toolkit} — 工具集，通过 {@code @Tool} 注解自动注册</li>
 *   <li>{@link ToolRegistry} — 工具注册表，管理所有已注册工具</li>
 *   <li>{@code McpClientManager} — MCP 协议工具，替换 AAF 自研 McpToolService</li>
 *   <li>{@code SubAgentTool} — 将 Agent 作为工具调用（Agent-as-Tool 模式）</li>
 * </ul>
 *
 * <p>TODO: 将 AAF 现有工具（BuiltinSkills 等）迁移为 AgentScope @Tool 注解方式注册。
 */
@Slf4j
@RequiredArgsConstructor
public class AgentScopeToolAdapter {

    private final ToolRegistry toolRegistry;

    /**
     * 获取所有已注册工具（用于注入 Agent 系统提示词）。
     */
    public List<RegisteredToolFunction> listTools() {
        return toolRegistry.getRegisteredTools();
    }

    /**
     * 注册工具集（Spring Bean 初始化时调用）。
     *
     * @param toolkit 工具集实例（含 @Tool 注解方法）
     */
    public void register(Toolkit toolkit) {
        toolRegistry.register(toolkit);
        log.info("注册工具集: {}", toolkit.getClass().getSimpleName());
    }
}
