package com.xuejiai.aaf.framework.intelligent.infrastructure.governance;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.engine.tool.ConnectorToolCallback;
import com.xuejiai.aaf.framework.engine.tool.ToolRegistry;
import com.xuejiai.aaf.framework.intelligent.agent.port.ConnectorActionPort;
import com.xuejiai.aaf.framework.intelligent.agent.port.CredentialVaultPort;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort.ToolInvocationResult;
import com.xuejiai.aaf.framework.intelligent.infrastructure.governance.persistence.ConnectorActionExecutionEntity;
import com.xuejiai.aaf.framework.intelligent.infrastructure.governance.persistence.ConnectorActionExecutionRepository;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** 现有工具目录的连接器动作适配；vaultRef 和幂等键仅在基础设施边界可见。 */
public final class RegistryConnectorActionAdapter implements ConnectorActionPort {
    private static final String SUCCEEDED = "SUCCEEDED";

    private final ToolRegistry registry;
    private final CredentialVaultPort credentials;
    private final ConnectorActionExecutionRepository executions;

    public RegistryConnectorActionAdapter(
            ToolRegistry registry,
            CredentialVaultPort credentials,
            ConnectorActionExecutionRepository executions) {
        this.registry = Objects.requireNonNull(registry, "registry 不能为空");
        this.credentials = Objects.requireNonNull(credentials, "credentials 不能为空");
        this.executions = Objects.requireNonNull(executions, "executions 不能为空");
    }

    @Override
    public Mono<ToolInvocationResult> invoke(ConnectorAction action) {
        return Mono.fromCallable(() -> invokeBlocking(action))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private ToolInvocationResult invokeBlocking(ConnectorAction action) {
        var context = action.context();
        var credential = credentials
                .findActiveHandle(
                        context.tenantId(),
                        context.userId(),
                        action.credentialHandle(),
                        action.connectorId(),
                        action.requiredScopes(),
                        Instant.now())
                .orElseThrow(() -> new IllegalStateException("连接器凭证句柄无效或已撤销"));
        var callback = registry
                .getCallback(action.tool().name())
                .orElseThrow(() -> new IllegalStateException(
                        "连接器动作未注册: " + action.tool().name()));
        if (!(callback instanceof ConnectorToolCallback connector)) {
            throw new IllegalStateException(
                    "连接器动作必须实现 ConnectorToolCallback: " + action.tool().name());
        }
        if (!action.writeAction()) {
            var output = connector.callConnector(
                    action.arguments(), credential.vaultRef(), action.idempotencyKey());
            return result(action, Objects.requireNonNull(output, "Connector 返回结果不能为空"), false);
        }

        var digest = requestDigest(action);
        var now = Instant.now();
        executions.claim(
                action.idempotencyKey(),
                context.tenantId().value(),
                context.userId().value(),
                context.taskId().value(),
                context.executionId().value(),
                action.connectorId(),
                action.tool().name(),
                digest,
                action.idempotencyKey(),
                now);
        var execution = requireSame(action, digest, requireExecution(action.idempotencyKey()));
        if (SUCCEEDED.equals(execution.getStatus())) {
            return result(action, execution.getResult(), true);
        }
        if (executions.markAttempt(action.idempotencyKey(), Instant.now()) == 0) {
            var concurrent = requireSame(action, digest, requireExecution(action.idempotencyKey()));
            if (SUCCEEDED.equals(concurrent.getStatus())) {
                return result(action, concurrent.getResult(), true);
            }
            throw new IllegalStateException("Connector 幂等执行状态无法抢占: " + action.idempotencyKey());
        }

        try {
            var output = Objects.requireNonNull(
                    connector.callConnector(
                            action.arguments(), credential.vaultRef(), action.idempotencyKey()),
                    "Connector 返回结果不能为空");
            if (executions.complete(action.idempotencyKey(), output, Instant.now()) == 0) {
                var concurrent = requireSame(
                        action, digest, requireExecution(action.idempotencyKey()));
                if (!SUCCEEDED.equals(concurrent.getStatus())
                        || !Objects.equals(concurrent.getResult(), output)) {
                    throw new IllegalStateException(
                            "Connector 同幂等键返回结果不一致: " + action.idempotencyKey());
                }
                return result(action, concurrent.getResult(), true);
            }
            return result(action, output, false);
        } catch (RuntimeException failure) {
            executions.recordFailure(
                    action.idempotencyKey(), truncate(failure.getMessage()), Instant.now());
            throw failure;
        }
    }

    private ConnectorActionExecutionEntity requireExecution(String key) {
        return executions.findById(key)
                .orElseThrow(() -> new IllegalStateException("Connector 幂等 claim 状态丢失: " + key));
    }

    private ConnectorActionExecutionEntity requireSame(
            ConnectorAction action, String digest, ConnectorActionExecutionEntity execution) {
        var context = action.context();
        var same = execution.getTenantId().equals(context.tenantId().value())
                && execution.getUserId().equals(context.userId().value())
                && execution.getTaskId().equals(context.taskId().value())
                && execution.getExecutionId().equals(context.executionId().value())
                && execution.getConnectorId().equals(action.connectorId())
                && execution.getActionName().equals(action.tool().name())
                && execution.getRequestDigest().equals(digest)
                && execution.getProviderIdempotencyKey().equals(action.idempotencyKey());
        if (!same) {
            throw new IllegalStateException(
                    "Connector 幂等键已绑定不同请求: " + action.idempotencyKey());
        }
        return execution;
    }

    private ToolInvocationResult result(
            ConnectorAction action, String output, boolean replayed) {
        return new ToolInvocationResult(
                output,
                Map.of(
                        "connectorAction", action.tool().name(),
                        "idempotentReplay", replayed));
    }

    private String requestDigest(ConnectorAction action) {
        var request = new TreeMap<String, Object>();
        request.put("tenantId", action.context().tenantId().value());
        request.put("userId", action.context().userId().value());
        request.put("taskId", action.context().taskId().value());
        request.put("executionId", action.context().executionId().value());
        request.put("connectorId", action.connectorId());
        request.put("actionName", action.tool().name());
        request.put("arguments", canonicalValue(action.arguments()));
        return sha256(JsonUtils.toJsonString(request));
    }

    private Object canonicalValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            var result = new TreeMap<String, Object>();
            for (var entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    throw new IllegalArgumentException("Connector 参数 key 必须是字符串");
                }
                result.put(key, canonicalValue(entry.getValue()));
            }
            return result;
        }
        if (value instanceof Iterable<?> iterable) {
            var result = new ArrayList<>();
            iterable.forEach(item -> result.add(canonicalValue(item)));
            return result;
        }
        return value;
    }

    private String sha256(String value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("运行环境缺少 SHA-256", failure);
        }
    }

    private String truncate(String message) {
        if (message == null) {
            return "Connector 调用失败";
        }
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }
}
