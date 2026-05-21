package com.xuejiai.aaf.framework.intelligent.agent.agentscope;

import java.util.List;
import java.util.Set;

import io.agentscope.core.model.ToolSchema;
import io.agentscope.core.tool.Toolkit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * AgentScope Toolkit → AAF 工具引擎适配器。
 *
 * <p>适配策略：委托给 AgentScope {@link Toolkit} 公开 API， 替换 AAF 自研的 {@code ToolRegistry} 和 {@code
 * ToolCallDispatcher}。
 *
 * <p>AgentScope 工具体系：
 *
 * <ul>
 *   <li>{@link Toolkit} — 工具管理门面（注册、查询、执行）
 *   <li>{@code McpClientManager} — MCP 协议工具，替换 AAF 自研 McpToolService
 *   <li>{@code SubAgentTool} — 将 Agent 作为工具调用（Agent-as-Tool 模式）
 * </ul>
 *
 * <p>TODO: 将 AAF 现有工具（BuiltinSkills 等）迁移为 AgentScope @Tool 注解方式注册。
 */
@Slf4j
@RequiredArgsConstructor
public class AgentScopeToolAdapter {

    private final Toolkit toolkit;

    /** 获取所有已注册工具的 Schema（用于注入 Agent 系统提示词）。 */
    public List<ToolSchema> listToolSchemas() {
        return toolkit.getToolSchemas();
    }

    /** 获取所有已注册工具名称。 */
    public Set<String> getToolNames() {
        return toolkit.getToolNames();
    }

    /**
     * 注册工具对象（扫描 @Tool 注解方法）。
     *
     * @param toolObject 含 @Tool 注解方法的对象
     */
    public void register(Object toolObject) {
        toolkit.registerTool(toolObject);
        log.info("注册工具集: {}", toolObject.getClass().getSimpleName());
    }
}
