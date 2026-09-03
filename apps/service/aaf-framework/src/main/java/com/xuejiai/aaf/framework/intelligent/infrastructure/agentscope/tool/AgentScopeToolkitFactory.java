package com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.tool;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.ObjectProvider;

import com.xuejiai.aaf.framework.intelligent.agent.model.ToolRef;
import com.xuejiai.aaf.framework.intelligent.agent.port.ContextAwareToolHandler;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolCatalogPort;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolCatalogPort.ToolDefinition;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolGatewayPort;

import io.agentscope.core.tool.Toolkit;

/**
 * 将版本化 AAF 工具定义编译为 AgentScope Toolkit。
 *
 * <p>Toolkit 只包含精确匹配请求版本的工具，且每个工具都被包成 {@link PortBackedAgentTool}， 确保模型无法绕过 AAF 权限与审计链路。
 *
 * <p><b>内置工具绕开 {@code ai_tool_catalog}（架构修正，见 {@link ContextAwareToolHandler} 类注释）</b>：实现 {@link
 * ContextAwareToolHandler} 的内置系统工具（{@code submit_executor_plan}、{@code report_executor_step}
 * 等）随代码发布，治理元数据是编译期确定的代码属性，不需要运营方在数据库里配置——本工厂优先按 {@code ref.name()} 查找已注册的 {@link
 * ContextAwareToolHandler}，命中则直接用它自身声明的 {@code readOnly()}/{@code reversible()}/{@code
 * inputSchema()} 构造 {@link ToolDefinition}，不查目录；
 * 只有查不到对应内置处理器的工具（Connector/MCP，回调运行时动态注册，权限确需运营方随时调整）才继续走 {@code toolCatalog.resolve(...)}
 * 的数据库治理路径。两条路径最终都汇入同一个 {@link PortBackedAgentTool}， 鉴权链路（{@code
 * DefaultToolGateway}）完全不受影响——只是元数据来源换了，授权检查、fencing、receipt 均照常生效。
 */
public final class AgentScopeToolkitFactory {

    private final ToolCatalogPort toolCatalog;
    private final ToolGatewayPort toolGateway;
    private final ToolResultEvidenceStore evidenceStore;
    private final ObjectProvider<ContextAwareToolHandler> builtinHandlers;

    public AgentScopeToolkitFactory(
            ToolCatalogPort toolCatalog,
            ToolGatewayPort toolGateway,
            ToolResultEvidenceStore evidenceStore,
            ObjectProvider<ContextAwareToolHandler> builtinHandlers) {
        this.toolCatalog = toolCatalog;
        this.toolGateway = toolGateway;
        this.evidenceStore = evidenceStore;
        this.builtinHandlers = builtinHandlers;
    }

    /** 目录返回结果必须与请求逐项同序对应，缺项或错序一律视为目录故障（仅适用于走目录路径的工具）。 */
    public Toolkit create(List<ToolRef> refs) {
        var toolkit = new Toolkit();
        var catalogRefs = new ArrayList<ToolRef>();
        var catalogPositions = new ArrayList<Integer>();
        var definitions = new ToolDefinition[refs.size()];
        for (var index = 0; index < refs.size(); index++) {
            var ref = refs.get(index);
            var builtin = findBuiltinHandler(ref.name());
            if (builtin != null) {
                definitions[index] = builtinDefinition(ref, builtin);
            } else {
                catalogRefs.add(ref);
                catalogPositions.add(index);
            }
        }
        if (!catalogRefs.isEmpty()) {
            var resolved = toolCatalog.resolve(catalogRefs);
            if (resolved.size() != catalogRefs.size()) {
                throw new IllegalStateException("工具目录未返回全部精确版本定义");
            }
            for (var i = 0; i < catalogRefs.size(); i++) {
                var expected = catalogRefs.get(i);
                var definition = resolved.get(i);
                if (!expected.equals(definition.ref())) {
                    throw new IllegalStateException("工具目录返回了错误或乱序的工具定义: " + expected);
                }
                definitions[catalogPositions.get(i)] = definition;
            }
        }
        for (var definition : definitions) {
            toolkit.registerAgentTool(
                    new PortBackedAgentTool(definition, toolGateway, evidenceStore));
        }
        return toolkit;
    }

    /** 内置工具的 {@code ToolDefinition} 完全由代码自身声明构造，不经过数据库；{@code source} 恒为 {@code LOCAL}。 */
    private static ToolDefinition builtinDefinition(ToolRef ref, ContextAwareToolHandler handler) {
        return new ToolDefinition(
                ref,
                handler.description(),
                handler.inputSchema(),
                "LOCAL",
                "",
                handler.readOnly(),
                handler.reversible(),
                handler.requireConfirm(),
                false);
    }

    private ContextAwareToolHandler findBuiltinHandler(String toolName) {
        return builtinHandlers
                .orderedStream()
                .filter(handler -> handler.toolName().equals(toolName))
                .findFirst()
                .orElse(null);
    }
}
