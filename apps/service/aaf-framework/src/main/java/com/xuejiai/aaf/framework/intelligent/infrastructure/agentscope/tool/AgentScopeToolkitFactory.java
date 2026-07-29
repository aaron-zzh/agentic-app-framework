package com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.tool;

import java.util.List;

import com.xuejiai.aaf.framework.intelligent.agent.model.ToolRef;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolCatalogPort;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolGatewayPort;

import io.agentscope.core.tool.Toolkit;

/** 将版本化 AAF 工具定义编译为 AgentScope Toolkit。 */
public final class AgentScopeToolkitFactory {

    private final ToolCatalogPort toolCatalog;
    private final ToolGatewayPort toolGateway;
    private final ToolResultEvidenceStore evidenceStore;

    public AgentScopeToolkitFactory(
            ToolCatalogPort toolCatalog,
            ToolGatewayPort toolGateway,
            ToolResultEvidenceStore evidenceStore) {
        this.toolCatalog = toolCatalog;
        this.toolGateway = toolGateway;
        this.evidenceStore = evidenceStore;
    }

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
                    new PortBackedAgentTool(definition, toolGateway, evidenceStore));
        }
        return toolkit;
    }
}
