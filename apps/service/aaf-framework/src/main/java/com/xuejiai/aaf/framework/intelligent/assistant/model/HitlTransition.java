package com.xuejiai.aaf.framework.intelligent.assistant.model;

import java.util.Objects;

import com.xuejiai.aaf.framework.intelligent.agent.model.AuthorizationGrant;
import com.xuejiai.aaf.framework.intelligent.agent.model.InvocationContext;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ExecutionEventStatus;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.OwnerType;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventType;

/** HITL 原子提交命令；不定义 Task/Execution/TaskPlan 之外的持久状态。 */
public final class HitlTransition {
    private HitlTransition() {}

    public record AuthorizationRequestTransition(HumanApproval approval, ExecutionEvent event) {
        public AuthorizationRequestTransition {
            Objects.requireNonNull(approval, "approval 不能为空");
            Objects.requireNonNull(event, "event 不能为空");
            var context = approval.invocationContext();
            if (approval.status() != HumanApproval.Status.PENDING
                    || !persistentControlMode(context.controlMode())
                    || context.executionContract() == null
                    || context.parentExecutionId() == null) {
                throw new IllegalArgumentException("授权请求必须是携带父 execution 与合同的持久 PENDING 审批");
            }
            if (!event.tenantId().equals(context.tenantId())
                    || !event.userId().equals(context.userId())
                    || !event.conversationId().equals(context.conversationId())
                    || !event.sessionId().equals(context.sessionId())
                    || !event.taskId().equals(context.taskId())
                    || !event.executionId().equals(context.executionId())
                    || !Objects.equals(event.parentExecutionId(), context.parentExecutionId())
                    || event.controlMode() != context.controlMode()
                    || event.type() != ExecutionEventType.AUTHORIZATION_REQUESTED
                    || event.status() != ExecutionEventStatus.AWAITING_AUTHORIZATION) {
                throw new IllegalArgumentException("授权请求与事件边界不一致");
            }
        }
    }

    public record ClarificationRequestTransition(
            InvocationContext parentContext,
            com.xuejiai.aaf.framework.intelligent.assistant.model.ClarificationRequest
                    clarification,
            ExecutionEvent event) {
        public ClarificationRequestTransition {
            Objects.requireNonNull(parentContext, "parentContext 不能为空");
            Objects.requireNonNull(clarification, "clarification 不能为空");
            Objects.requireNonNull(event, "event 不能为空");
            if (parentContext.taskId() == null
                    || parentContext.nodeIdentity() == null
                    || !clarification.taskId().equals(parentContext.taskId())
                    || !clarification.executionId().equals(parentContext.executionId())
                    || !clarification.nodeId().equals(parentContext.nodeIdentity().nodeId())
                    || clarification.status()
                            != com.xuejiai.aaf.framework.intelligent.assistant.model
                                    .ClarificationRequest.Status.PENDING
                    || !event.tenantId().equals(parentContext.tenantId())
                    || !event.userId().equals(parentContext.userId())
                    || !event.conversationId().equals(parentContext.conversationId())
                    || !event.sessionId().equals(parentContext.sessionId())
                    || !event.taskId().equals(parentContext.taskId())
                    || !event.executionId().equals(parentContext.executionId())
                    || !event.runId().equals(parentContext.runId())
                    || !Objects.equals(event.parentExecutionId(), parentContext.parentExecutionId())
                    || event.controlMode() != parentContext.controlMode()
                    || !Objects.equals(event.assistantId(), parentContext.assistantId())
                    || !event.correlationId().equals(parentContext.correlationId())
                    || !Objects.equals(event.causationId(), parentContext.causationId())
                    || !Objects.equals(event.idempotencyKey(), parentContext.idempotencyKey())
                    || !Objects.equals(event.nodeIdentity(), parentContext.nodeIdentity())
                    || event.type() != ExecutionEventType.CLARIFICATION_REQUESTED
                    || event.status() != ExecutionEventStatus.AWAITING_CLARIFICATION
                    || event.ownerType() != OwnerType.ASSISTANT
                    || !clarification
                            .requestId()
                            .equals(
                                    Objects.toString(
                                            event.payload().values().get("requestId"), ""))) {
                throw new IllegalArgumentException("澄清请求与执行事件边界不一致");
            }
        }
    }

    public record AuthorizationDecision(
            HumanApproval approval, AuthorizationGrant grant, ExecutionEvent event) {
        public AuthorizationDecision {
            Objects.requireNonNull(approval, "approval 不能为空");
            Objects.requireNonNull(event, "event 不能为空");
            if (approval.status() == HumanApproval.Status.PENDING) {
                throw new IllegalArgumentException("授权决定不能为 PENDING");
            }
            var approved = approval.status() == HumanApproval.Status.APPROVED;
            if (approved != (grant != null)) {
                throw new IllegalArgumentException("批准决定必须且仅能携带授权事实");
            }
            var context = approval.invocationContext();
            if (!context.userId().value().equals(approval.decidedBy())) {
                throw new IllegalArgumentException("只能由审批关联的认证用户作出决定");
            }
            var expectedType =
                    approved
                            ? ExecutionEventType.AUTHORIZATION_GRANTED
                            : ExecutionEventType.AUTHORIZATION_DENIED;
            var expectedStatus =
                    approved ? ExecutionEventStatus.RECOVERING : ExecutionEventStatus.PAUSED;
            if (!event.tenantId().equals(context.tenantId())
                    || !event.userId().equals(context.userId())
                    || !event.conversationId().equals(context.conversationId())
                    || !event.sessionId().equals(context.sessionId())
                    || !event.taskId().equals(context.taskId())
                    || !event.executionId().equals(context.executionId())
                    || !Objects.equals(event.parentExecutionId(), context.parentExecutionId())
                    || event.controlMode() != context.controlMode()
                    || event.type() != expectedType
                    || event.status() != expectedStatus
                    || event.ownerType() != OwnerType.HUMAN) {
                throw new IllegalArgumentException("授权决定与事件边界不一致");
            }
            if (grant != null
                    && (!grant.tenantId().equals(context.tenantId())
                            || !grant.taskId().equals(context.taskId())
                            || !grant.action().equals(approval.action())
                            || !grant.resource().equals(approval.resource())
                            || !grant.grantedBy().equals(approval.decidedBy())
                            || !grant.grantedAt().equals(approval.decidedAt()))) {
                throw new IllegalArgumentException("授权决定与授权事实边界不一致");
            }
        }
    }

    private static boolean persistentControlMode(ExecutionEvent.ControlMode mode) {
        return mode == ExecutionEvent.ControlMode.READ_ONLY
                || mode == ExecutionEvent.ControlMode.COLLABORATIVE
                || mode == ExecutionEvent.ControlMode.DELEGATED;
    }
}
