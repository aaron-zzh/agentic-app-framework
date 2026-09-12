package com.xuejiai.aaf.framework.intelligent.assistant.application;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.xuejiai.aaf.framework.intelligent.agent.model.AuthorizationGrant;
import com.xuejiai.aaf.framework.intelligent.agent.port.AuthorizationGrantPort;
import com.xuejiai.aaf.framework.intelligent.agent.port.CredentialVaultPort;
import com.xuejiai.aaf.framework.intelligent.assistant.model.HitlTransition.AuthorizationDecision;
import com.xuejiai.aaf.framework.intelligent.assistant.model.HumanApproval;
import com.xuejiai.aaf.framework.intelligent.assistant.port.ConversationLeasePort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.HitlCoordinatorPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.HitlTransitionPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.HumanApprovalPort;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ExecutionEventStatus;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.OwnerType;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventPayload;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventType;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.EventId;

import lombok.extern.slf4j.Slf4j;

/** 不轮询、不占用执行线程的持久 HITL 协调器。 */
@Slf4j
public final class PersistentHitlCoordinator implements HitlCoordinatorPort {

    private final HumanApprovalPort approvals;
    private final AuthorizationGrantPort grants;
    private final CredentialVaultPort credentials;
    private final HitlTransitionPort transitions;
    private final TaskCommandService taskCommands;
    private final ConversationLeasePort leases;

    public PersistentHitlCoordinator(
            HumanApprovalPort approvals,
            AuthorizationGrantPort grants,
            CredentialVaultPort credentials,
            HitlTransitionPort transitions,
            TaskCommandService taskCommands,
            ConversationLeasePort leases) {
        this.approvals = Objects.requireNonNull(approvals, "approvals 不能为空");
        this.grants = Objects.requireNonNull(grants, "grants 不能为空");
        this.credentials = Objects.requireNonNull(credentials, "credentials 不能为空");
        this.transitions = Objects.requireNonNull(transitions, "transitions 不能为空");
        this.taskCommands = Objects.requireNonNull(taskCommands, "taskCommands 不能为空");
        this.leases = Objects.requireNonNull(leases, "leases 不能为空");
    }

    @Override
    public HumanApproval decide(DecisionCommand command) {
        Objects.requireNonNull(command, "command 不能为空");
        var pending =
                approvals
                        .find(command.tenantId(), command.approvalId())
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "审批不存在: " + command.approvalId()));
        requireDecisionOwner(pending, command.decidedBy());
        if (!isDelegated(pending.invocationContext())) {
            throw new IllegalStateException("审批未绑定 canonical TaskNode execution");
        }
        return decideDelegated(pending, command);
    }

    private HumanApproval decideDelegated(HumanApproval current, DecisionCommand command) {
        var decided = decidedApproval(current, command);
        var grant =
                decided.status() == HumanApproval.Status.APPROVED
                        ? authorizationGrant(decided)
                        : null;
        var transition = new AuthorizationDecision(decided, grant, decisionEvent(decided));
        var context = decided.invocationContext();
        var lease =
                leases.acquire(
                                context.tenantId(),
                                context.conversationId(),
                                "approval-transition-" + decided.approvalId(),
                                Duration.ofSeconds(30))
                        .orElseThrow(
                                () -> new IllegalStateException("无法取得授权决策 conversation lease"));
        final HumanApproval stored;
        try {
            stored = transitions.decideAuthorization(transition, lease);
        } finally {
            leases.release(lease);
        }
        if (stored.status() == HumanApproval.Status.APPROVED) {
            taskCommands.resumeReadyNodes(
                    context.tenantId(),
                    context.taskId(),
                    context.executionId(),
                    Objects.requireNonNull(stored.decidedAt(), "批准决定缺少 decidedAt"));
        }
        log.debug(
                "[HITL] 委托审批 transition 已提交：taskId={}，executionId={}，approvalId={}，结果={}",
                context.taskId().value(),
                context.executionId().value(),
                stored.approvalId(),
                stored.status());
        return stored;
    }

    private static HumanApproval decidedApproval(HumanApproval current, DecisionCommand command) {
        if (current.status() != HumanApproval.Status.PENDING) {
            if (current.status() != command.decision()
                    || !Objects.equals(current.decidedBy(), command.decidedBy())
                    || !Objects.equals(current.decisionReason(), command.reason())) {
                throw new IllegalStateException("approvalId 已绑定不同决定事实");
            }
            return current;
        }
        return new HumanApproval(
                current.approvalId(),
                current.invocationContext(),
                current.action(),
                current.resource(),
                current.reason(),
                current.impact(),
                current.dataUsage(),
                current.remediation(),
                current.requestedConditions(),
                current.reversible(),
                command.decision(),
                current.createdAt(),
                command.at(),
                command.decidedBy(),
                command.reason());
    }

    private AuthorizationGrant authorizationGrant(HumanApproval approval) {
        var grantId = "approval-" + approval.approvalId();
        var existing =
                grants
                        .list(
                                approval.invocationContext().tenantId(),
                                approval.invocationContext().taskId())
                        .stream()
                        .filter(grant -> grant.grantId().equals(grantId))
                        .findFirst();
        if (existing.isPresent()) {
            return existing.get();
        }
        var context = approval.invocationContext();
        var requested = new LinkedHashMap<>(approval.requestedConditions());
        var scope = requireCondition(requested.remove("scope"), "scope");
        var expiresAt = Instant.parse(requireCondition(requested.remove("expiresAt"), "expiresAt"));
        String credentialHandle = null;
        var connectorId = requested.get("connector");
        if (connectorId != null) {
            var credential =
                    credentials
                            .findActive(
                                    context.tenantId(),
                                    context.userId(),
                                    connectorId,
                                    scopes(requested.get("credentialScopes")),
                                    approval.decidedAt())
                            .orElseThrow(
                                    () -> new IllegalStateException("当前认证用户没有满足 scope 的活跃连接器句柄"));
            credentialHandle = credential.handleId();
        }
        return new AuthorizationGrant(
                grantId,
                context.tenantId(),
                context.taskId(),
                approval.action(),
                approval.resource(),
                scope,
                expiresAt,
                requested,
                approval.reversible(),
                credentialHandle,
                approval.decidedBy(),
                approval.decidedAt(),
                null);
    }

    private static ExecutionEvent decisionEvent(HumanApproval approval) {
        var context = approval.invocationContext();
        var values = new LinkedHashMap<String, Object>();
        values.put("approvalId", approval.approvalId());
        values.put("action", approval.action());
        values.put("resource", approval.resource());
        values.put("decision", approval.status().name());
        var type =
                approval.status() == HumanApproval.Status.APPROVED
                        ? ExecutionEventType.AUTHORIZATION_GRANTED
                        : ExecutionEventType.AUTHORIZATION_DENIED;
        return new ExecutionEvent(
                new EventId("approval-decision-" + approval.approvalId()),
                context.tenantId(),
                context.conversationId(),
                context.sessionId(),
                context.taskId(),
                context.executionId(),
                context.runId(),
                context.parentExecutionId(),
                1,
                type,
                approval.status() == HumanApproval.Status.APPROVED
                        ? ExecutionEventStatus.RECOVERING
                        : ExecutionEventStatus.PAUSED,
                context.controlMode(),
                OwnerType.HUMAN,
                context.assistantId(),
                null,
                context.userId(),
                context.correlationId(),
                context.causationId(),
                context.idempotencyKey(),
                new ExecutionEventPayload(values),
                approval.decidedAt(),
                context.nodeIdentity());
    }

    private static boolean isDelegated(
            com.xuejiai.aaf.framework.intelligent.agent.model.InvocationContext context) {
        return context.parentExecutionId() != null && context.executionContract() != null;
    }

    private static void requireDecisionOwner(HumanApproval approval, String decidedBy) {
        if (!approval.invocationContext().userId().value().equals(decidedBy)) {
            throw new IllegalArgumentException("只能由审批关联的认证用户作出决定");
        }
    }

    private static String requireCondition(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("审批缺少授权条件: " + name);
        }
        return value;
    }

    private static Set<String> scopes(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return Stream.of(value.split(","))
                .map(String::trim)
                .filter(scope -> !scope.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }
}
