package com.xuejiai.aaf.framework.intelligent.agent.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.xuejiai.aaf.framework.intelligent.agent.model.AuthorizationGrant;
import com.xuejiai.aaf.framework.intelligent.agent.port.AuthorizationGrantPort;
import com.xuejiai.aaf.framework.intelligent.agent.port.ConnectorActionPort;
import com.xuejiai.aaf.framework.intelligent.agent.port.ConnectorActionPort.ConnectorAction;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolCatalogPort.ToolDefinition;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolGatewayPort;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort.ToolInvocation;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort.ToolInvocationResult;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolParameterPolicyPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.HitlCoordinatorPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.HitlCoordinatorPort.ApprovalCommand;
import reactor.core.publisher.Mono;

/** 在真实 Agent 工具边界执行可见性、持久 grant 与参数策略三层检查。 */
public final class DefaultToolGateway implements ToolGatewayPort {

    private static final String TASK_SCOPE = "TASK";

    private final AuthorizationGrantPort grants;
    private final ToolParameterPolicyPort parameterPolicy;
    private final HitlCoordinatorPort hitl;
    private final ToolInvocationPort localTools;
    private final ConnectorActionPort connectors;

    public DefaultToolGateway(
            AuthorizationGrantPort grants,
            ToolParameterPolicyPort parameterPolicy,
            HitlCoordinatorPort hitl,
            ToolInvocationPort localTools,
            ConnectorActionPort connectors) {
        this.grants = Objects.requireNonNull(grants, "grants 不能为空");
        this.parameterPolicy = Objects.requireNonNull(parameterPolicy, "parameterPolicy 不能为空");
        this.hitl = Objects.requireNonNull(hitl, "hitl 不能为空");
        this.localTools = Objects.requireNonNull(localTools, "localTools 不能为空");
        this.connectors = Objects.requireNonNull(connectors, "connectors 不能为空");
    }

    @Override
    public Mono<ToolInvocationResult> invoke(ToolDefinition definition, ToolInvocation invocation) {
        var context = invocation.context();
        var rule = context.toolAuthorization().requireVisible(
                context.controlMode(),
                definition.ref().name(),
                definition.readOnly(),
                definition.reversible());
        var resource = resource(invocation.arguments(), definition.ref().toolId());
        if ("*".equals(resource)) {
            return Mono.error(new IllegalArgumentException("普通工具审批禁止通配 resource"));
        }
        var grantConditions = grantConditions(definition);
        var grantRequired = !definition.readOnly()
                || definition.connectorAction()
                || rule.authorizationRequired()
                || definition.requireConfirm();
        AuthorizationGrant grant = null;
        if (grantRequired) {
            grant = grants.findActive(
                            context.tenantId(),
                            context.taskId(),
                            definition.ref().name(),
                            resource,
                            TASK_SCOPE,
                            grantConditions,
                            definition.reversible(),
                            Instant.now())
                    .orElse(null);
            if (grant == null) {
                var requested = new LinkedHashMap<>(grantConditions);
                requested.put("scope", TASK_SCOPE);
                requested.put("expiresAt", Instant.now().plus(15, ChronoUnit.MINUTES).toString());
                var approval = hitl.request(new ApprovalCommand(
                        context,
                        definition.ref().name(),
                        resource,
                        "当前任务需要执行受控工具动作",
                        "将访问或修改资源 " + resource,
                        "仅使用当前工具 schema 中声明的参数",
                        definition.reversible() ? "可通过对应补偿动作撤销" : "不可自动撤销，需人工补救",
                        requested,
                        definition.reversible(),
                        Instant.now()));
                return Mono.error(new ApprovalRequiredException(
                        approval.approvalId(), "工具需要用户授权: " + definition.ref().name()));
            }
        }
        parameterPolicy.validate(definition, invocation.arguments(), context, grant);
        if (!definition.connectorAction()) {
            return localTools.invoke(invocation);
        }
        if (grant == null || grant.credentialHandle() == null) {
            return Mono.error(new ConnectorCredentialUnavailableException(
                    "连接器凭证已过期、撤销或尚未绑定，请重新授权连接器"));
        }
        if (!definition.readOnly() && !definition.idempotencyRequired()) {
            return Mono.error(new IllegalStateException("Connector 写动作缺少目录幂等声明"));
        }
        return connectors.invoke(new ConnectorAction(
                invocation.toolCallId(),
                invocation.tool(),
                definition.ref().toolId(),
                grant.credentialHandle(),
                requiredScopes(definition),
                invocation.arguments(),
                trustedIdempotencyKey(definition, invocation),
                !definition.readOnly(),
                context));
    }

    private static String trustedIdempotencyKey(
            ToolDefinition definition, ToolInvocation invocation) {
        var context = invocation.context();
        var contextKey = context.idempotencyKey();
        if (!definition.readOnly() && contextKey == null) {
            throw new IllegalStateException("Connector 写动作缺少受信 InvocationContext 幂等键");
        }
        var trustedRoot = contextKey == null
                ? context.correlationId().value()
                : contextKey.value();
        var source = String.join(
                "|",
                trustedRoot,
                context.tenantId().value(),
                context.taskId().value(),
                context.executionId().value(),
                definition.ref().toolId(),
                invocation.toolCallId());
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return "connector:" + HexFormat.of().formatHex(
                    digest.digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("运行环境缺少 SHA-256", failure);
        }
    }

    private static Map<String, String> grantConditions(ToolDefinition definition) {
        if (!definition.connectorAction()) {
            return Map.of();
        }
        return Map.of(
                "connector", definition.ref().toolId(),
                "credentialScopes", String.join(",", requiredScopes(definition)));
    }

    private static Set<String> requiredScopes(ToolDefinition definition) {
        if (definition.permissionCode().isBlank()) {
            return Set.of();
        }
        return Stream.of(definition.permissionCode().split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String resource(Map<String, Object> arguments, String defaultResource) {
        var value = arguments.getOrDefault("resource", arguments.get("resourceId"));
        return value == null || value.toString().isBlank() ? defaultResource : value.toString();
    }

    /** 可通过重新授权连接器恢复的状态。 */
    public static final class ConnectorCredentialUnavailableException extends IllegalStateException {
        public ConnectorCredentialUnavailableException(String message) {
            super(message);
        }
    }
}
