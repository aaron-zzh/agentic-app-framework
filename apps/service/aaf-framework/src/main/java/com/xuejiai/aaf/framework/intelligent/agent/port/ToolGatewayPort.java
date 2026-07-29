package com.xuejiai.aaf.framework.intelligent.agent.port;

import com.xuejiai.aaf.framework.intelligent.agent.port.ToolCatalogPort.ToolDefinition;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort.ToolInvocation;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort.ToolInvocationResult;

import reactor.core.publisher.Mono;

/** Agent 工具边界的三层治理入口。 */
public interface ToolGatewayPort {

    Mono<ToolInvocationResult> invoke(ToolDefinition definition, ToolInvocation invocation);

    final class ApprovalRequiredException extends IllegalStateException {
        private final String approvalId;

        public ApprovalRequiredException(String approvalId, String message) {
            super(message);
            this.approvalId = approvalId;
        }

        public String approvalId() {
            return approvalId;
        }
    }
}
