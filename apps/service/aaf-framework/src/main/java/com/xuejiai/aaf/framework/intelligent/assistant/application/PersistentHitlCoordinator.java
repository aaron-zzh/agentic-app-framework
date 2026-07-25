package com.xuejiai.aaf.framework.intelligent.assistant.application;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.xuejiai.aaf.framework.intelligent.agent.model.AuthorizationGrant;
import com.xuejiai.aaf.framework.intelligent.agent.port.AuthorizationGrantPort;
import com.xuejiai.aaf.framework.intelligent.agent.port.CredentialVaultPort;
import com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantTask.OwnerKind;
import com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantTask.RecoveryPoint;
import com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantTask.TaskActor;
import com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantTask.TaskOwner;
import com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantTask.TaskStatus;
import com.xuejiai.aaf.framework.intelligent.assistant.model.HumanApproval;
import com.xuejiai.aaf.framework.intelligent.assistant.port.HitlCoordinatorPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.HumanApprovalPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskControlPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskRecoveryPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskResumeSignalPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskResumeSignalPort.ResumeSignal;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ExecutionEventStatus;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.OwnerType;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventPayload;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventStorePort;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventType;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.EventId;

/** 不轮询、不占用执行线程的持久 HITL 协调器。 */
public final class PersistentHitlCoordinator implements HitlCoordinatorPort {

    private final HumanApprovalPort approvals;
    private final AuthorizationGrantPort grants;
    private final CredentialVaultPort credentials;
    private final TaskControlPort tasks;
    private final TaskRecoveryPort recoveries;
    private final TaskResumeSignalPort resumeSignals;
    private final ExecutionEventStorePort events;

    public PersistentHitlCoordinator(
            HumanApprovalPort approvals,
            AuthorizationGrantPort grants,
            CredentialVaultPort credentials,
            TaskControlPort tasks,
            TaskRecoveryPort recoveries,
            TaskResumeSignalPort resumeSignals,
            ExecutionEventStorePort events) {
        this.approvals = Objects.requireNonNull(approvals, "approvals 不能为空");
        this.grants = Objects.requireNonNull(grants, "grants 不能为空");
        this.credentials = Objects.requireNonNull(credentials, "credentials 不能为空");
        this.tasks = Objects.requireNonNull(tasks, "tasks 不能为空");
        this.recoveries = Objects.requireNonNull(recoveries, "recoveries 不能为空");
        this.resumeSignals = Objects.requireNonNull(resumeSignals, "resumeSignals 不能为空");
        this.events = Objects.requireNonNull(events, "events 不能为空");
    }

    @Override
    public HumanApproval request(ApprovalCommand command) {
        Objects.requireNonNull(command, "command 不能为空");
        var approval = new HumanApproval(
                randomId(),
                command.context(),
                command.action(),
                command.resource(),
                command.reason(),
                command.impact(),
                command.dataUsage(),
                command.remediation(),
                command.requestedConditions(),
                command.reversible(),
                HumanApproval.Status.PENDING,
                command.at(),
                null,
                null,
                null);
        return approvals.create(approval);
    }

    @Override
    public HumanApproval decide(DecisionCommand command) {
        Objects.requireNonNull(command, "command 不能为空");
        var pending = approvals.find(command.tenantId(), command.approvalId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "审批不存在: " + command.approvalId()));
        requireDecisionOwner(pending, command.decidedBy());
        var result = approvals.decide(
                command.tenantId(),
                command.approvalId(),
                command.decision(),
                command.decidedBy(),
                command.reason(),
                command.at());
        var approval = result.approval();
        requireDecisionOwner(approval, command.decidedBy());
        if (approval.status() == HumanApproval.Status.APPROVED) {
            ensureGrant(approval);
            appendDecisionEvent(approval);
            recoveries.schedule(approval, command.at());
            var context = approval.invocationContext();
            resumeSignals.publish(new ResumeSignal(
                    context.tenantId(),
                    context.taskId(),
                    context.executionId(),
                    approval.approvalId(),
                    true,
                    approval.decidedAt()));
            return approval;
        }
        pauseRejectedTask(approval);
        appendDecisionEvent(approval);
        return approval;
    }

    private void ensureGrant(HumanApproval approval) {
        var context = approval.invocationContext();
        var requested = new LinkedHashMap<>(approval.requestedConditions());
        var scope = requireCondition(requested.remove("scope"), "scope");
        var expiresAt = Instant.parse(requireCondition(requested.remove("expiresAt"), "expiresAt"));
        String credentialHandle = null;
        var connectorId = requested.get("connector");
        if (connectorId != null) {
            var credential = credentials.findActive(
                            context.tenantId(),
                            context.userId(),
                            connectorId,
                            scopes(requested.get("credentialScopes")),
                            approval.decidedAt())
                    .orElseThrow(() -> new IllegalStateException(
                            "当前认证用户没有满足 scope 的活跃连接器句柄"));
            credentialHandle = credential.handleId();
        }
        grants.grant(new AuthorizationGrant(
                "approval-" + approval.approvalId(),
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
                null));
    }

    private void pauseRejectedTask(HumanApproval approval) {
        var context = approval.invocationContext();
        var task = tasks.find(context.tenantId(), context.taskId())
                .orElseThrow(() -> new IllegalStateException("审批关联任务不存在"));
        if (task.status() != TaskStatus.AWAITING_AUTHORIZATION) {
            if (task.status() == TaskStatus.PAUSED) {
                return;
            }
            throw new IllegalStateException("拒绝决定对应任务状态非法: " + task.status());
        }
        var paused = task.transitionTo(
                TaskStatus.PAUSED,
                "用户拒绝工具授权",
                new TaskActor(OwnerKind.HUMAN, approval.decidedBy()),
                new TaskOwner(OwnerKind.HUMAN, context.userId().value()),
                new RecoveryPoint(
                        "approval-rejected:" + approval.approvalId(),
                        "修改方案后重新规划"),
                approval.decidedAt());
        tasks.save(context.tenantId(), paused);
    }

    private void appendDecisionEvent(HumanApproval approval) {
        var context = approval.invocationContext();
        var values = new LinkedHashMap<String, Object>();
        values.put("approvalId", approval.approvalId());
        values.put("action", approval.action());
        values.put("resource", approval.resource());
        values.put("decision", approval.status().name());
        var type = approval.status() == HumanApproval.Status.APPROVED
                ? ExecutionEventType.AUTHORIZATION_GRANTED
                : ExecutionEventType.AUTHORIZATION_DENIED;
        var event = new ExecutionEvent(
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
                approval.decidedAt());
        events.append(event).block();
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

    private static String randomId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
