package com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.tool;

import java.util.List;

import com.xuejiai.aaf.framework.intelligent.agent.model.ToolRef;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolCatalogPort;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort;
import io.agentscope.core.tool.Toolkit;

/** 将版本化 AAF 工具定义编译为 AgentScope Toolkit。 */
public final class AgentScopeToolkitFactory {

    private final ToolCatalogPort toolCatalog;
    private final ToolInvocationPort toolInvocation;
    private final ToolResultEvidenceStore evidenceStore;

    public AgentScopeToolkitFactory(
            ToolCatalogPort toolCatalog,
            ToolInvocationPort toolInvocation,
            ToolResultEvidenceStore evidenceStore) {
        this.toolCatalog = toolCatalog;
        this.toolInvocation = toolInvocation;
        this.evidenceStore = evidenceStore;
    }

    /** 严格按引用解析工具；缺失或错序时失败，不走替代路径。 */
    public Toolkit create(List<ToolRef> refs) {
        var definitions = toolCatalog.resolve(refs);
        if (definitions.size() != refs.size()) {
            throw new IllegalStateException("工具目录未返回全部精确版本定义");
        }

        var toolkit = new Toolkit();
        for (var index = 0; index < refs.size(); index++) {
            var expected = refs.get(index);
            var definition = definitions.get(index);
            if (!expected.equals(definition.ref())) {
                throw new IllegalStateException("工具目录返回了错误或乱序的工具定义: " + expected);
            }
            toolkit.registerAgentTool(
                    new PortBackedAgentTool(definition, toolInvocation, evidenceStore));
        }
        return toolkit;
    }
}
