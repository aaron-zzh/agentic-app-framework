package com.xuejiai.aaf.framework.intelligent.agent.port;

import java.util.Map;
import java.util.Objects;

import com.xuejiai.aaf.framework.intelligent.agent.model.InvocationContext;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort.ToolInvocationResult;

import reactor.core.publisher.Mono;

/** MCP 业务调用边界；凭据由基础设施按受信上下文解析。 */
public interface McpPort {

    Mono<ToolInvocationResult> invoke(McpInvocation invocation);

    record McpInvocation(
            InvocationContext context,
            String serverId,
            String toolName,
            String credentialHandle,
            String idempotencyKey,
            Map<String, Object> businessArguments) {
        public McpInvocation {
            Objects.requireNonNull(context, "context 不能为空");
            if (serverId == null
                    || serverId.isBlank()
                    || toolName == null
                    || toolName.isBlank()
                    || credentialHandle == null
                    || credentialHandle.isBlank()
                    || idempotencyKey == null
                    || idempotencyKey.isBlank()) {
                throw new IllegalArgumentException("MCP 受信调用字段不能为空白");
            }
            businessArguments =
                    Map.copyOf(Objects.requireNonNull(businessArguments, "businessArguments 不能为空"));
        }
    }
}
