package com.xuejiai.aaf.framework.intelligent.agent.port;

import java.util.Map;

import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort.ToolInvocation;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort.ToolInvocationResult;

import reactor.core.publisher.Mono;

/**
 * 需要 InvocationContext 的内置工具处理器。
 *
 * <p><b>治理元数据自声明（对齐官方 {@code AgentTool}/{@code ToolBase} 契约）</b>：内置系统工具随代码发布，
 * {@code readOnly}/{@code reversible}/{@code inputSchema} 是代码固有属性，不是运营方需要在后台配置的可变数据——
 * 与 Connector/MCP 类工具（回调运行时动态注册、权限确需运营方随时调整）性质不同。核实官方 <a
 * href="https://java.agentscope.io/v2/zh/docs/building-blocks/tool.html">Tool 文档</a> 后确认，官方
 * {@code AgentTool}/{@code ToolBase} 本身就要求工具自己声明 {@code isReadOnly()}/{@code getParameters()}，
 * 不依赖外部数据库治理。{@code AgentScopeToolkitFactory} 对内置工具（实现本接口）优先使用这里声明的元数据，
 * 不再强制查询 {@code ai_tool_catalog}；只有 Connector/MCP 类工具才继续走目录治理。
 *
 * <p>默认值定义为最严格的安全基线（非只读、可撤销、需确认），实现类必须显式覆盖才能放宽——防止漏声明导致的权限降级。
 */
public interface ContextAwareToolHandler {

    String toolName();

    String description();

    Mono<ToolInvocationResult> invoke(ToolInvocation invocation);

    /** 是否只读、不产生业务副作用。默认 {@code false}（最严格基线），只读工具必须显式覆盖为 {@code true}。 */
    default boolean readOnly() {
        return false;
    }

    /**
     * 是否可撤销的写操作。与 {@code readOnly} 互斥（不能同时为 {@code true}，对齐 {@code
     * ToolCatalogPort.ToolDefinition} 的不变量）。默认 {@code false}。
     */
    default boolean reversible() {
        return false;
    }

    /** 是否强制要求人工确认（不可撤销的高风险动作）。默认 {@code false}。 */
    default boolean requireConfirm() {
        return false;
    }

    /**
     * 参数 JSON Schema，对齐官方 {@code ToolBase.getParameters()}。默认空 schema（无参数），
     * 有入参的工具必须显式覆盖，否则模型看不到正确的调用契约。
     */
    default Map<String, Object> inputSchema() {
        return Map.of("type", "object", "properties", Map.of());
    }
}
