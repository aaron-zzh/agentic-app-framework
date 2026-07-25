package com.xuejiai.aaf.framework.intelligent.agent.port;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.xuejiai.aaf.framework.intelligent.agent.model.InvocationContext;
import com.xuejiai.aaf.framework.intelligent.agent.model.ToolRef;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort.ToolInvocationResult;
import reactor.core.publisher.Mono;

/** 外部连接器业务动作边界；凭证明文和 vaultRef 永不进入模型参数。 */
public interface ConnectorActionPort {

    Mono<ToolInvocationResult> invoke(ConnectorAction action);

    record ConnectorAction(
            String toolCallId,
            ToolRef tool,
            String connectorId,
            String credentialHandle,
            Set<String> requiredScopes,
            Map<String, Object> arguments,
            String idempotencyKey,
            boolean writeAction,
            InvocationContext context) {
        public ConnectorAction {
            Objects.requireNonNull(toolCallId, "toolCallId 不能为空");
            Objects.requireNonNull(tool, "tool 不能为空");
            Objects.requireNonNull(connectorId, "connectorId 不能为空");
            Objects.requireNonNull(credentialHandle, "credentialHandle 不能为空");
            requiredScopes = requiredScopes == null ? Set.of() : Set.copyOf(requiredScopes);
            arguments = Map.copyOf(Objects.requireNonNull(arguments, "arguments 不能为空"));
            Objects.requireNonNull(idempotencyKey, "idempotencyKey 不能为空");
            Objects.requireNonNull(context, "context 不能为空");
            if (connectorId.isBlank()
                    || credentialHandle.isBlank()
                    || idempotencyKey.isBlank()) {
                throw new IllegalArgumentException(
                        "connectorId、credentialHandle 和 idempotencyKey 不能为空白");
            }
        }
    }
}
