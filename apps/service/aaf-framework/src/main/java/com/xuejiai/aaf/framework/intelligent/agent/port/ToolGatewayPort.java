package com.xuejiai.aaf.framework.intelligent.agent.port;

import com.xuejiai.aaf.framework.intelligent.agent.port.ToolCatalogPort.ToolDefinition;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort.ToolInvocation;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort.ToolInvocationResult;

import reactor.core.publisher.Mono;

/** Agent 工具边界的三层治理入口。 */
public interface ToolGatewayPort {

    /** 工具结果 metadata / 证据中的控制交接标记：为 true 时本轮循环挂起并交出控制权，不再调用模型。 */
    String CONTROL_HANDOFF_KEY = "controlHandoff";

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

    /** 轻量态触及授权工具：网关已携带该调用把当前执行升级为 Task，本轮循环须立即交出控制权。 */
    final class PromotionHandoffException extends IllegalStateException {
        private final String promotedTaskId;

        public PromotionHandoffException(String promotedTaskId, String message) {
            super(message);
            this.promotedTaskId = promotedTaskId;
        }

        public String promotedTaskId() {
            return promotedTaskId;
        }
    }
}
