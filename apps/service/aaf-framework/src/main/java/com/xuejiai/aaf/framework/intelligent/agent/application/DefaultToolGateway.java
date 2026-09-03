package com.xuejiai.aaf.framework.intelligent.agent.application;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.xuejiai.aaf.framework.intelligent.agent.model.AuthorizationGrant;
import com.xuejiai.aaf.framework.intelligent.agent.port.AuthorizationGrantPort;
import com.xuejiai.aaf.framework.intelligent.agent.port.ConnectorActionPort;
import com.xuejiai.aaf.framework.intelligent.agent.port.ConnectorActionPort.ConnectorAction;
import com.xuejiai.aaf.framework.intelligent.agent.port.InvocationReceiptPort;
import com.xuejiai.aaf.framework.intelligent.agent.port.InvocationReceiptPort.Disposition;
import com.xuejiai.aaf.framework.intelligent.agent.port.InvocationReceiptPort.ReceiptRequest;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolCatalogPort.ToolDefinition;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolGatewayPort;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort.ToolInvocation;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort.ToolInvocationResult;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolParameterPolicyPort;
import com.xuejiai.aaf.framework.intelligent.assistant.model.HumanApproval;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskTransition.AuthorizationRequestTransition;
import com.xuejiai.aaf.framework.intelligent.assistant.port.ConversationLeasePort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.DelegatedTaskPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskTransitionPort;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ExecutionEventStatus;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.OwnerType;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventPayload;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventType;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.EventId;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/** Agent 工具唯一生产入口：授权、fencing、预算和统一 receipt 均 fail-closed。 */
@Slf4j
public final class DefaultToolGateway implements ToolGatewayPort {
    private static final String TASK_SCOPE = "TASK";

    private final AuthorizationGrantPort grants;
    private final ToolParameterPolicyPort parameterPolicy;
    private final TaskTransitionPort transitions;
    private final ToolInvocationPort localTools;
    private final ConnectorActionPort connectors;
    private final ConversationLeasePort leases;
    private final DelegatedTaskPort delegatedTasks;
    private final InvocationReceiptPort receipts;

    public DefaultToolGateway(
            AuthorizationGrantPort grants,
            ToolParameterPolicyPort parameterPolicy,
            TaskTransitionPort transitions,
            ToolInvocationPort localTools,
            ConnectorActionPort connectors,
            ConversationLeasePort leases,
            DelegatedTaskPort delegatedTasks,
            InvocationReceiptPort receipts) {
        this.grants = Objects.requireNonNull(grants, "grants 不能为空");
        this.parameterPolicy = Objects.requireNonNull(parameterPolicy, "parameterPolicy 不能为空");
        this.transitions = Objects.requireNonNull(transitions, "transitions 不能为空");
        this.localTools = Objects.requireNonNull(localTools, "localTools 不能为空");
        this.connectors = Objects.requireNonNull(connectors, "connectors 不能为空");
        this.leases = Objects.requireNonNull(leases, "leases 不能为空");
        this.delegatedTasks = Objects.requireNonNull(delegatedTasks, "delegatedTasks 不能为空");
        this.receipts = Objects.requireNonNull(receipts, "receipts 不能为空");
    }

    @Override
    public Mono<ToolInvocationResult> invoke(ToolDefinition definition, ToolInvocation invocation) {
        var context = invocation.context();
        requireCurrent(context);
        var rule =
                context.toolAuthorization()
                        .requireVisible(
                                context.controlMode(),
                                definition.ref().name(),
                                definition.readOnly(),
                                definition.reversible());
        var resource = resource(invocation.arguments(), definition.ref().toolId());
        if ("*".equals(resource)) {
            return Mono.error(new IllegalArgumentException("普通工具审批禁止通配 resource"));
        }
        var conditions = grantConditions(definition);
        var grantRequired =
                !definition.readOnly()
                        || definition.connectorAction()
                        || rule.authorizationRequired()
                        || definition.requireConfirm();
        log.debug(
                "[工具网关] 工具调用已通过可见性校验：taskId={}，tool={}，只读={}，可撤销={}，需要授权={}",
                context.taskId().value(),
                definition.ref().name(),
                definition.readOnly(),
                definition.reversible(),
                grantRequired);
        if (rule.missingGrantBehavior()
                == com.xuejiai.aaf.framework.intelligent.agent.model.ToolAuthorizationContext
                        .MissingGrantBehavior.DENY) {
            return Mono.error(
                    new AuthorizationDeniedException("当前执行策略拒绝授权工具动作: " + definition.ref().name()));
        }
        AuthorizationGrant grant = null;
        if (grantRequired) {
            grant =
                    grants.findActive(
                                    context.tenantId(),
                                    context.taskId(),
                                    definition.ref().name(),
                                    resource,
                                    TASK_SCOPE,
                                    conditions,
                                    definition.reversible(),
                                    Instant.now())
                            .orElse(null);
            if (grant == null
                    && rule.missingGrantBehavior()
                            == com.xuejiai.aaf.framework.intelligent.agent.model
                                    .ToolAuthorizationContext.MissingGrantBehavior
                                    .PREAUTHORIZED_ONLY) {
                return Mono.error(new IllegalStateException("工具缺少预授权: " + definition.ref().name()));
            }
            if (grant == null) {
                var requested = new LinkedHashMap<>(conditions);
                requested.put("scope", TASK_SCOPE);
                requested.put("expiresAt", Instant.now().plus(15, ChronoUnit.MINUTES).toString());
                var approvalId = UUID.randomUUID().toString().replace("-", "");
                var requestedAt = Instant.now();
                var pendingApproval =
                        new HumanApproval(
                                approvalId,
                                context,
                                definition.ref().name(),
                                resource,
                                "当前任务需要执行受控工具动作",
                                "将访问或修改受控资源",
                                "仅使用当前工具 schema 中声明的参数",
                                definition.reversible() ? "可通过对应补偿动作撤销" : "不可自动撤销，需人工补救",
                                requested,
                                definition.reversible(),
                                HumanApproval.Status.PENDING,
                                requestedAt,
                                null,
                                null,
                                null);
                var approval =
                        transitions.requestAuthorization(
                                new AuthorizationRequestTransition(
                                        pendingApproval,
                                        authorizationRequestEvent(pendingApproval)));
                log.debug(
                        "[工具网关] 未找到有效授权，已原子提交 HITL 等待状态：taskId={}，tool={}，approvalId={}，可撤销={}",
                        context.taskId().value(),
                        definition.ref().name(),
                        approval.approvalId(),
                        definition.reversible());
                return Mono.error(
                        new ApprovalRequiredException(
                                approval.approvalId(), "工具需要用户授权: " + definition.ref().name()));
            }
        }
        parameterPolicy.validate(definition, invocation.arguments(), context, grant);
        if (definition.connectorAction() && (grant == null || grant.credentialHandle() == null)) {
            return Mono.error(
                    new ConnectorCredentialUnavailableException("连接器凭证已过期、撤销或尚未绑定，请重新授权连接器"));
        }
        if (definition.connectorAction()
                && !definition.readOnly()
                && !definition.idempotencyRequired()) {
            return Mono.error(new IllegalStateException("Connector 写动作缺少目录幂等声明"));
        }

        var finalGrant = grant;
        if (definition.readOnly()) {
            return executeAndAccount(definition, invocation, finalGrant, null);
        }
        var actionKey = requireActionKey(invocation);
        var receiptKey = stableReceiptKey(definition, invocation, actionKey);
        var digest = requestDigest(definition, invocation, resource);
        var claim =
                receipts.claim(
                        new ReceiptRequest(
                                receiptKey,
                                digest,
                                definition.ref().toolId(),
                                actionKey,
                                context,
                                Instant.now()));
        if (claim.disposition() == Disposition.REPLAY) {
            return Mono.just(claim.existingResult());
        }
        if (claim.disposition() == Disposition.IN_PROGRESS) {
            return Mono.error(new IllegalStateException("同一工具动作正在执行: " + receiptKey));
        }
        return executeAndAccount(definition, invocation, finalGrant, receiptKey)
                .onErrorResume(
                        failure -> {
                            if (!(failure instanceof DelegatedTaskPort.BudgetExceededException)) {
                                receipts.fail(
                                        receiptKey, context, failure.getMessage(), Instant.now());
                            }
                            return Mono.error(failure);
                        });
    }

    private Mono<ToolInvocationResult> executeAndAccount(
            ToolDefinition definition,
            ToolInvocation invocation,
            AuthorizationGrant grant,
            String receiptKey) {
        var context = invocation.context();
        var delegated =
                context.controlMode()
                        == com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent
                                .ControlMode.DELEGATED;
        if (delegated) {
            delegatedTasks.reserveToolCall(context, definition.ref().name(), Instant.now());
        }
        Mono<ToolInvocationResult> execution;
        log.debug(
                "[工具网关] 授权与参数策略校验通过，开始派发工具：taskId={}，tool={}，连接器={}，写入={}，委托执行={}",
                context.taskId().value(),
                definition.ref().name(),
                definition.connectorAction(),
                !definition.readOnly(),
                delegated);
        if (!definition.connectorAction()) {
            execution = localTools.invoke(invocation);
        } else {
            execution =
                    connectors.invoke(
                            new ConnectorAction(
                                    invocation.toolCallId(),
                                    invocation.tool(),
                                    definition.ref().toolId(),
                                    grant.credentialHandle(),
                                    requiredScopes(definition),
                                    invocation.arguments(),
                                    receiptKey == null
                                            ? "read:"
                                                    + sha256(
                                                            context.tenantId().value()
                                                                    + '|'
                                                                    + context.taskId().value()
                                                                    + '|'
                                                                    + definition.ref().toolId()
                                                                    + '|'
                                                                    + invocation.toolCallId())
                                            : receiptKey,
                                    !definition.readOnly(),
                                    context));
        }
        return execution.map(
                result -> {
                    requireCurrent(context);
                    if (receiptKey != null)
                        receipts.complete(receiptKey, context, result, Instant.now());
                    if (delegated) {
                        delegatedTasks.recordToolUsage(
                                context,
                                longMetadata(result, "toolUnits", 1),
                                decimalMetadata(result, "credits", BigDecimal.ZERO),
                                Instant.now());
                    }
                    return result;
                });
    }

    private void requireCurrent(
            com.xuejiai.aaf.framework.intelligent.agent.model.InvocationContext context) {
        if (context.lease() != null) leases.requireCurrent(context.lease());
        delegatedTasks.requireAgentExecution(context);
    }

    private static String requireActionKey(ToolInvocation invocation) {
        var key = invocation.context().idempotencyKey();
        if (key == null) throw new IllegalStateException("写工具缺少受信稳定 action key");
        var actionDigest =
                sha256(invocation.tool().toolId() + '|' + canonical(invocation.arguments()));
        return key.value() + ':' + actionDigest;
    }

    private static String stableReceiptKey(
            ToolDefinition definition, ToolInvocation invocation, String actionKey) {
        var context = invocation.context();
        var source =
                String.join(
                        "|",
                        context.tenantId().value(),
                        context.userId().value(),
                        context.taskId().value(),
                        definition.ref().toolId(),
                        actionKey);
        return "tool:" + sha256(source);
    }

    private static String requestDigest(
            ToolDefinition definition, ToolInvocation invocation, String resource) {
        var request = new TreeMap<String, Object>();
        request.put("tenantId", invocation.context().tenantId().value());
        request.put("userId", invocation.context().userId().value());
        request.put("taskId", invocation.context().taskId().value());
        request.put("toolId", definition.ref().toolId());
        request.put("resource", resource);
        request.put("arguments", canonical(invocation.arguments()));
        return sha256(request.toString());
    }

    private static Object canonical(Object value) {
        if (value instanceof Map<?, ?> map) {
            var result = new TreeMap<String, Object>();
            map.forEach((key, item) -> result.put(key.toString(), canonical(item)));
            return result;
        }
        if (value instanceof Iterable<?> values) {
            var result = new ArrayList<>();
            values.forEach(item -> result.add(canonical(item)));
            return result;
        }
        return value;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("运行环境缺少 SHA-256", failure);
        }
    }

    private static ExecutionEvent authorizationRequestEvent(HumanApproval approval) {
        var context = approval.invocationContext();
        return new ExecutionEvent(
                new EventId("approval-request-" + approval.approvalId()),
                context.tenantId(),
                context.conversationId(),
                context.sessionId(),
                context.taskId(),
                context.executionId(),
                context.runId(),
                context.parentExecutionId(),
                1,
                ExecutionEventType.AUTHORIZATION_REQUESTED,
                ExecutionEventStatus.AWAITING_AUTHORIZATION,
                context.controlMode(),
                OwnerType.SYSTEM,
                context.assistantId(),
                null,
                context.userId(),
                context.correlationId(),
                context.causationId(),
                context.idempotencyKey(),
                new ExecutionEventPayload(
                        Map.of(
                                "approvalId",
                                approval.approvalId(),
                                "action",
                                approval.action(),
                                "reversible",
                                approval.reversible())),
                approval.createdAt(),
                context.nodeIdentity());
    }

    private static long longMetadata(ToolInvocationResult result, String key, long fallback) {
        var value = result.metadata().get(key);
        return value instanceof Number number ? number.longValue() : fallback;
    }

    private static BigDecimal decimalMetadata(
            ToolInvocationResult result, String key, BigDecimal fallback) {
        var value = result.metadata().get(key);
        if (value == null) return fallback;
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException failure) {
            throw new IllegalStateException("工具返回非法积分用量: " + value, failure);
        }
    }

    private static Map<String, String> grantConditions(ToolDefinition definition) {
        if (!definition.connectorAction()) return Map.of();
        return Map.of(
                "connector",
                definition.ref().toolId(),
                "credentialScopes",
                String.join(",", requiredScopes(definition)));
    }

    private static Set<String> requiredScopes(ToolDefinition definition) {
        if (definition.permissionCode().isBlank()) return Set.of();
        return Stream.of(definition.permissionCode().split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String resource(Map<String, Object> arguments, String defaultResource) {
        var value = arguments.getOrDefault("resource", arguments.get("resourceId"));
        return value == null || value.toString().isBlank() ? defaultResource : value.toString();
    }

    public static final class AuthorizationDeniedException extends IllegalStateException {
        public AuthorizationDeniedException(String message) {
            super(message);
        }
    }

    public static final class ConnectorCredentialUnavailableException
            extends IllegalStateException {
        public ConnectorCredentialUnavailableException(String message) {
            super(message);
        }
    }
}
