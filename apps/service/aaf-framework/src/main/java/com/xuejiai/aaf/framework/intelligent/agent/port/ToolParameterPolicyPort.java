package com.xuejiai.aaf.framework.intelligent.agent.port;

import java.util.Map;

import com.xuejiai.aaf.framework.intelligent.agent.model.AuthorizationGrant;
import com.xuejiai.aaf.framework.intelligent.agent.model.InvocationContext;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolCatalogPort.ToolDefinition;

/** 工具实际参数的系统与租户策略检查边界。 */
public interface ToolParameterPolicyPort {

    void validate(
            ToolDefinition definition,
            Map<String, Object> arguments,
            InvocationContext context,
            AuthorizationGrant grant);
}
