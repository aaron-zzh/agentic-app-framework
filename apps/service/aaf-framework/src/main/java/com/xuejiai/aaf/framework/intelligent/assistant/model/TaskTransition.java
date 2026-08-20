package com.xuejiai.aaf.framework.intelligent.assistant.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.xuejiai.aaf.framework.intelligent.agent.model.AuthorizationGrant;
import com.xuejiai.aaf.framework.intelligent.agent.model.InvocationContext;
import com.xuejiai.aaf.framework.intelligent.assistant.application.AssistantCommand;
import com.xuejiai.aaf.framework.intelligent.assistant.model.DelegatedTask.Status;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ExecutionEventStatus;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.OwnerType;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventType;

/** 委托任务原子提交边界；状态始终取自 DelegatedTask，不定义第二套状态机。 */
public record TaskTransition(
        DelegatedTask task,
        AssistantCommand command,
        TaskBoard board,
        List<ExecutionEvent> events) {

    public TaskTransition {
        Objects.requireNonNull(task, "task 不能为空");
        Objects.requireNonNull(command, "command 不能为空");
        Objects.requireNonNull(board, "board 不能为空");
        events = List.copyOf(Objects.requireNonNull(events, "events 不能为空"));
        if (events.isEmpty()) {
            throw new IllegalArgumentException("TaskTransition 至少包含一个事件");
        }
        if (!task.tenantId().equals(command.tenantId())
                || !task.userId().equals(command.userId())
                || !task.taskId().equals(command.taskId())
                || !task.conversationId().equals(command.conversationId())
                || !task.sessionId().equals(command.sessionId())
                || !task.executionId().equals(command.executionId())
                || !Objects.equals(task.parentExecutionId(), command.parentExecutionId())
                || !task.contract().equals(command.executionContract())
                || !persistentControlMode(command.controlMode())
                || !task.taskId().equals(board.taskId())
                || events.stream()
                        .anyMatch(
                                event ->
                                        !event.tenantId().equals(task.tenantId())
                                                || !event.userId().equals(task.userId())
                                                || !event.taskId().equals(task.taskId())
                                                || !event.conversationId()
                                                        .equals(task.conversationId())
                                                || !event.sessionId().equals(task.sessionId())
                                                || !event.executionId().equals(task.executionId())
                                                || event.controlMode() != command.controlMode())) {
            throw new IllegalArgumentException("TaskTransition 的 task、command、board、event 边界不一致");
        }
    }

    private static boolean persistentControlMode(ExecutionEvent.ControlMode mode) {
        return mode == ExecutionEvent.ControlMode.READ_ONLY
                || mode == ExecutionEvent.ControlMode.COLLABORATIVE
                || mode == ExecutionEvent.ControlMode.DELEGATED;
    }

    /** 持久子任务请求工具授权时，审批、父任务、TaskBoard、事件与 outbox 的原子提交事实。 */
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

    /** 委托子任务进入结构化澄清等待时的原子提交事实。 */
    public record ClarificationRequestTransition(
            InvocationContext parentContext,
            ClarificationRequest clarification,
            ExecutionEvent event) {

        public ClarificationRequestTransition {
            Objects.requireNonNull(parentContext, "parentContext 不能为空");
            Objects.requireNonNull(clarification, "clarification 不能为空");
            Objects.requireNonNull(event, "event 不能为空");
            if (!clarification.taskId().equals(parentContext.taskId())
                    || clarification.status() != ClarificationRequest.Status.PENDING
                    || !event.tenantId().equals(parentContext.tenantId())
                    || !event.userId().equals(parentContext.userId())
                    || !event.taskId().equals(parentContext.taskId())
                    || !event.executionId().equals(parentContext.executionId())
                    || event.type() != ExecutionEventType.CLARIFICATION_REQUESTED
                    || event.status() != ExecutionEventStatus.AWAITING_CLARIFICATION
                    || event.ownerType() != OwnerType.ASSISTANT) {
                throw new IllegalArgumentException("澄清请求与父执行事件边界不一致");
            }
        }
    }

    /** evaluator 严格决策及有界停止的原子提交事实。 */
    public record IterationEvaluationTransition(
            InvocationContext context,
            String evaluatorSubTaskId,
            IterationEvaluation evaluation,
            ExecutionEvent event,
            Instant at) {

        public IterationEvaluationTransition {
            Objects.requireNonNull(context, "context 不能为空");
            if (evaluatorSubTaskId == null || evaluatorSubTaskId.isBlank()) {
                throw new IllegalArgumentException("evaluatorSubTaskId 不能为空白");
            }
            Objects.requireNonNull(evaluation, "evaluation 不能为空");
            Objects.requireNonNull(event, "event 不能为空");
            Objects.requireNonNull(at, "at 不能为空");
            if (!event.tenantId().equals(context.tenantId())
                    || !event.userId().equals(context.userId())
                    || !event.taskId().equals(context.taskId())
                    || !event.executionId().equals(context.executionId())
                    || event.type() != ExecutionEventType.ITERATION_EVALUATED
                    || event.ownerType() != OwnerType.ASSISTANT) {
                throw new IllegalArgumentException("迭代决策与父执行事件边界不一致");
            }
        }
    }

    /** 持有父 execution 租约的输入消费请求。 */
    public record InputTransition(InvocationContext context, Instant at) {
        public InputTransition {
            Objects.requireNonNull(context, "context 不能为空");
            Objects.requireNonNull(at, "at 不能为空");
        }
    }

    /** 已原子提交的澄清输入结果。 */
    public record InputCommit(
            DelegatedTask task,
            TaskBoard board,
            ClarificationRequest clarification,
            List<ExecutionEvent> events) {
        public InputCommit {
            Objects.requireNonNull(task, "task 不能为空");
            Objects.requireNonNull(board, "board 不能为空");
            events = List.copyOf(Objects.requireNonNull(events, "events 不能为空"));
            if (!task.taskId().equals(board.taskId())) {
                throw new IllegalArgumentException("输入提交的 task 与 board 边界不一致");
            }
        }
    }

    /** 已原子提交的 evaluator 决策与最新父任务/TaskBoard。 */
    public record IterationCommit(
            DelegatedTask task, TaskBoard board, ExecutionEvent event, boolean stopped) {
        public IterationCommit {
            Objects.requireNonNull(task, "task 不能为空");
            Objects.requireNonNull(board, "board 不能为空");
            Objects.requireNonNull(event, "event 不能为空");
            if (!task.taskId().equals(board.taskId())) {
                throw new IllegalArgumentException("迭代提交的 task 与 board 边界不一致");
            }
            if (stopped != (task.status() == Status.PAUSED)) {
                throw new IllegalArgumentException("迭代停止标记与父任务状态不一致");
            }
        }
    }

    /** 父执行异常后的失败或重试原子提交事实。 */
    public record ParentFailureTransition(
            InvocationContext context,
            String reason,
            boolean transientFailure,
            ExecutionEvent event,
            Instant at) {

        public ParentFailureTransition {
            Objects.requireNonNull(context, "context 不能为空");
            Objects.requireNonNull(event, "event 不能为空");
            Objects.requireNonNull(at, "at 不能为空");
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("父执行失败原因不能为空白");
            }
            if (!event.tenantId().equals(context.tenantId())
                    || !event.userId().equals(context.userId())
                    || !event.conversationId().equals(context.conversationId())
                    || !event.sessionId().equals(context.sessionId())
                    || !event.taskId().equals(context.taskId())
                    || !event.executionId().equals(context.executionId())
                    || event.controlMode() != context.controlMode()
                    || event.type() != ExecutionEventType.EXECUTION_FAILED
                    || event.status() != ExecutionEventStatus.FAILED
                    || event.ownerType() != OwnerType.ASSISTANT) {
                throw new IllegalArgumentException("父执行失败与事件边界不一致");
            }
        }
    }

    /** 父任务终态或暂停与对应 TaskBoard、事件的原子提交事实。 */
    public record ParentStateTransition(
            InvocationContext context,
            TaskBoard board,
            Status status,
            Map<String, Object> result,
            String reason,
            List<ExecutionEvent> events,
            Instant at) {

        public ParentStateTransition {
            Objects.requireNonNull(context, "context 不能为空");
            Objects.requireNonNull(board, "board 不能为空");
            Objects.requireNonNull(status, "status 不能为空");
            result = Map.copyOf(Objects.requireNonNull(result, "result 不能为空"));
            events = List.copyOf(Objects.requireNonNull(events, "events 不能为空"));
            Objects.requireNonNull(at, "at 不能为空");
            if (!board.taskId().equals(context.taskId())) {
                throw new IllegalArgumentException("父状态变更的 TaskBoard 与 context 边界不一致");
            }
            var expectedTypes = expectedTypes(status, board, result, reason);
            if (events.size() != expectedTypes.size()) {
                throw new IllegalArgumentException("父状态变更事件数量不符合终态契约");
            }
            for (var index = 0; index < events.size(); index++) {
                var event = events.get(index);
                var expectedType = expectedTypes.get(index);
                var expectedStatus =
                        expectedType == ExecutionEventType.MESSAGE_COMPLETED
                                ? ExecutionEventStatus.RUNNING
                                : switch (status) {
                                    case COMPLETED -> ExecutionEventStatus.COMPLETED;
                                    case FAILED -> ExecutionEventStatus.FAILED;
                                    case PAUSED -> ExecutionEventStatus.PAUSED;
                                    default -> throw new IllegalStateException("未支持的父状态变更");
                                };
                if (!event.tenantId().equals(context.tenantId())
                        || !event.conversationId().equals(context.conversationId())
                        || !event.taskId().equals(context.taskId())
                        || !event.executionId().equals(context.executionId())
                        || event.type() != expectedType
                        || event.status() != expectedStatus
                        || event.controlMode() != context.controlMode()
                        || event.ownerType() != OwnerType.ASSISTANT) {
                    throw new IllegalArgumentException("父状态变更与事件边界不一致");
                }
            }
        }

        private static List<ExecutionEventType> expectedTypes(
                Status status, TaskBoard board, Map<String, Object> result, String reason) {
            return switch (status) {
                case COMPLETED -> {
                    if (!board.completed() || result.isEmpty() || reason != null) {
                        throw new IllegalArgumentException("完成变更必须携带完成看板和结果");
                    }
                    yield List.of(
                            ExecutionEventType.MESSAGE_COMPLETED,
                            ExecutionEventType.EXECUTION_COMPLETED);
                }
                case FAILED -> {
                    if (!board.hasTerminalFailure() || !result.isEmpty() || blank(reason)) {
                        throw new IllegalArgumentException("失败变更必须携带终态失败看板和原因");
                    }
                    yield List.of(ExecutionEventType.EXECUTION_FAILED);
                }
                case PAUSED -> {
                    if (board.completed()
                            || board.hasTerminalFailure()
                            || board.hasRunning()
                            || !board.ready().isEmpty()
                            || !result.isEmpty()
                            || blank(reason)) {
                        throw new IllegalArgumentException("暂停变更必须对应无可运行节点的看板和原因");
                    }
                    yield List.of(ExecutionEventType.EXECUTION_PAUSED);
                }
                default -> throw new IllegalArgumentException("父状态变更只支持 COMPLETED/FAILED/PAUSED");
            };
        }

        private static boolean blank(String value) {
            return value == null || value.isBlank();
        }
    }

    /** 已原子提交的父任务与按存储序号回填后的事件。 */
    public record CommittedParent(DelegatedTask task, List<ExecutionEvent> events) {
        public CommittedParent {
            Objects.requireNonNull(task, "task 不能为空");
            events = List.copyOf(Objects.requireNonNull(events, "events 不能为空"));
            if (events.isEmpty()) {
                throw new IllegalArgumentException("父状态提交结果至少包含一个事件");
            }
        }
    }

    /** 委托任务 HITL 决策的原子提交事实。 */
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
}
