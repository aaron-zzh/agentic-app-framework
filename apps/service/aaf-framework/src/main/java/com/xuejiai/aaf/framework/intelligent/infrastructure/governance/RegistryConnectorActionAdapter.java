package com.xuejiai.aaf.framework.intelligent.infrastructure.governance;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

import com.xuejiai.aaf.framework.engine.tool.ConnectorToolCallback;
import com.xuejiai.aaf.framework.engine.tool.ToolRegistry;
import com.xuejiai.aaf.framework.intelligent.agent.port.ConnectorActionPort;
import com.xuejiai.aaf.framework.intelligent.agent.port.CredentialVaultPort;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort.ToolInvocationResult;
import com.xuejiai.aaf.framework.intelligent.assistant.port.ConversationLeasePort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.DelegatedTaskPort;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** 统一 invocation receipt 之后的 Connector provider 适配器。 */
public final class RegistryConnectorActionAdapter implements ConnectorActionPort {
    private final ToolRegistry registry;
    private final CredentialVaultPort credentials;
    private final ConversationLeasePort leases;
    private final DelegatedTaskPort tasks;

    public RegistryConnectorActionAdapter(
            ToolRegistry registry,
            CredentialVaultPort credentials,
            ConversationLeasePort leases,
            DelegatedTaskPort tasks) {
        this.registry = Objects.requireNonNull(registry, "registry 不能为空");
        this.credentials = Objects.requireNonNull(credentials, "credentials 不能为空");
        this.leases = Objects.requireNonNull(leases, "leases 不能为空");
        this.tasks = Objects.requireNonNull(tasks, "tasks 不能为空");
    }

    @Override
    public Mono<ToolInvocationResult> invoke(ConnectorAction action) {
        return Mono.fromCallable(() -> invokeBlocking(action)).subscribeOn(Schedulers.boundedElastic());
    }

    private ToolInvocationResult invokeBlocking(ConnectorAction action) {
        var context = action.context();
        if (context.lease() != null) leases.requireCurrent(context.lease());
        tasks.requireAgentExecution(context);
        var credential = credentials.findActiveHandle(
                        context.tenantId(), context.userId(), action.credentialHandle(),
                        action.connectorId(), action.requiredScopes(), Instant.now())
                .orElseThrow(() -> new IllegalStateException("连接器凭证句柄无效或已撤销"));
        var callback = registry.getCallback(action.tool().name())
                .orElseThrow(() -> new IllegalStateException("连接器动作未注册: " + action.tool().name()));
        if (!(callback instanceof ConnectorToolCallback connector)) {
            throw new IllegalStateException("连接器动作必须实现 ConnectorToolCallback: " + action.tool().name());
        }
        var output = Objects.requireNonNull(
                connector.callConnector(action.arguments(), credential.vaultRef(), action.idempotencyKey()),
                "Connector 返回结果不能为空");
        if (context.lease() != null) leases.requireCurrent(context.lease());
        tasks.requireAgentExecution(context);
        return new ToolInvocationResult(
                output,
                Map.of("connectorAction", action.tool().name(), "providerIdempotencyKey", action.idempotencyKey()));
    }
}
