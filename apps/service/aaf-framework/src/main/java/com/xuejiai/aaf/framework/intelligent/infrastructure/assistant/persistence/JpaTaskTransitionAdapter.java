package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.framework.intelligent.agent.model.AuthorizationGrant;
import com.xuejiai.aaf.framework.intelligent.agent.model.InvocationContext;
import com.xuejiai.aaf.framework.intelligent.assistant.application.AssistantCommand;
import com.xuejiai.aaf.framework.intelligent.assistant.model.ClarificationRequest;
import com.xuejiai.aaf.framework.intelligent.assistant.model.DelegatedTask;
import com.xuejiai.aaf.framework.intelligent.assistant.model.DelegatedTask.BudgetUsage;
import com.xuejiai.aaf.framework.intelligent.assistant.model.DelegatedTask.Owner;
import com.xuejiai.aaf.framework.intelligent.assistant.model.DelegatedTask.OwnerKind;
import com.xuejiai.aaf.framework.intelligent.assistant.model.DelegatedTask.Status;
import com.xuejiai.aaf.framework.intelligent.assistant.model.HumanApproval;
import com.xuejiai.aaf.framework.intelligent.assistant.model.InputBuffer;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskBoard.IterationStopReason;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskTransition;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskTransition.AuthorizationDecision;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskTransition.AuthorizationRequestTransition;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskTransition.ClarificationRequestTransition;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskTransition.CommittedParent;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskTransition.InputCommit;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskTransition.InputTransition;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskTransition.IterationCommit;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskTransition.IterationEvaluationTransition;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskTransition.ParentFailureTransition;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskTransition.ParentStateTransition;
import com.xuejiai.aaf.framework.intelligent.assistant.port.ConversationLeasePort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.ConversationLeasePort.Lease;
import com.xuejiai.aaf.framework.intelligent.assistant.port.DelegatedTaskPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskTransitionPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskTransitionPort.OutboxRelayException;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskTransitionPort.OutboxRelayFailureType;
import com.xuejiai.aaf.framework.intelligent.infrastructure.governance.persistence.AuthorizationGrantEntity;
import com.xuejiai.aaf.framework.intelligent.infrastructure.governance.persistence.AuthorizationGrantRepository;
import com.xuejiai.aaf.framework.intelligent.infrastructure.governance.persistence.HumanApprovalEntity;
import com.xuejiai.aaf.framework.intelligent.infrastructure.governance.persistence.HumanApprovalRepository;
import com.xuejiai.aaf.framework.intelligent.infrastructure.trace.persistence.SynchronousExecutionEventWriter;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ExecutionEventStatus;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.OwnerType;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventPayload;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventType;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.EventId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.RunId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.SessionId;

import io.micrometer.core.instrument.MeterRegistry;

/** PostgreSQL TaskTransition 原子提交适配器。 */
public final class JpaTaskTransitionAdapter implements TaskTransitionPort {
    private final DelegatedTaskRepository tasks;
    private final TaskBoardRepository boards;
    private final HumanApprovalRepository approvals;
    private final AuthorizationGrantRepository grants;
    private final ClarificationRequestRepository clarifications;
    private final TaskInputRepository inputs;
    private final SynchronousExecutionEventWriter eventWriter;
    private final TaskTransitionOutboxRepository outbox;
    private final ApplicationEventPublisher publisher;
    private final ConversationLeasePort leases;
    private final MeterRegistry meters;

    public JpaTaskTransitionAdapter(
            DelegatedTaskRepository tasks,
            TaskBoardRepository boards,
            HumanApprovalRepository approvals,
            AuthorizationGrantRepository grants,
            ClarificationRequestRepository clarifications,
            TaskInputRepository inputs,
            SynchronousExecutionEventWriter eventWriter,
            TaskTransitionOutboxRepository outbox,
            ApplicationEventPublisher publisher,
            ConversationLeasePort leases,
            MeterRegistry meters) {
        this.tasks = Objects.requireNonNull(tasks, "tasks 不能为空");
        this.boards = Objects.requireNonNull(boards, "boards 不能为空");
        this.approvals = Objects.requireNonNull(approvals, "approvals 不能为空");
        this.grants = Objects.requireNonNull(grants, "grants 不能为空");
        this.clarifications = Objects.requireNonNull(clarifications, "clarifications 不能为空");
        this.inputs = Objects.requireNonNull(inputs, "inputs 不能为空");
        this.eventWriter = Objects.requireNonNull(eventWriter, "eventWriter 不能为空");
        this.outbox = Objects.requireNonNull(outbox, "outbox 不能为空");
        this.publisher = Objects.requireNonNull(publisher, "publisher 不能为空");
        this.leases = Objects.requireNonNull(leases, "leases 不能为空");
        this.meters = Objects.requireNonNull(meters, "meters 不能为空");
    }

    @Override
    @Transactional
    public DelegatedTask create(TaskTransition transition) {
        Objects.requireNonNull(transition, "transition 不能为空");
        var task = transition.task();
        if (tasks.findByTenantIdAndTaskId(task.tenantId().value(), task.taskId().value())
                        .isPresent()
                || boards.findByTenantIdAndTaskId(task.tenantId().value(), task.taskId().value())
                        .isPresent()) {
            throw new IllegalStateException("委托任务已存在: " + task.taskId().value());
        }
        if (transition.command().executionContract() == null) {
            throw new IllegalArgumentException("委托任务命令缺少 ExecutionContract");
        }
        var taskEntity = new DelegatedTaskEntity();
        taskEntity.setCommand(transition.command());
        apply(taskEntity, task);
        var boardEntity = new TaskBoardEntity();
        boardEntity.setTenantId(task.tenantId().value());
        boardEntity.setTaskId(task.taskId().value());
        boardEntity.setBoard(transition.board());
        boardEntity.setFencingToken(0L);
        tasks.save(taskEntity);
        boards.save(boardEntity);
        appendEventsAndOutbox(transition.events(), 0L);
        return task;
    }

    @Override
    @Transactional
    public HumanApproval requestAuthorization(AuthorizationRequestTransition transition) {
        Objects.requireNonNull(transition, "transition 不能为空");
        var approval = transition.approval();
        var context = approval.invocationContext();
        var lease = context.lease();
        leases.requireCurrent(lease);
        requireLeaseBoundary(context, lease);

        // 固定加锁与写入顺序：task → board → approval → event → outbox。
        var taskEntity =
                tasks.findForUpdate(context.tenantId().value(), context.taskId().value())
                        .orElseThrow(() -> new IllegalStateException("授权请求关联委托任务不存在"));
        var current = taskEntity.getTask();
        requireActiveChildExecution(current, context, lease);
        var boardEntity =
                boards.findLockedByTenantIdAndTaskId(
                                context.tenantId().value(), context.taskId().value())
                        .orElseThrow(() -> new IllegalStateException("授权请求关联 TaskBoard 不存在"));
        requireCurrentBoardFence(boardEntity, lease);
        var waitingBoard =
                boardEntity
                        .getBoard()
                        .awaitAuthorization(context.executionId(), context.sessionId());
        if (approvals.existsById(approval.approvalId())) {
            throw new IllegalStateException("approvalId 已存在: " + approval.approvalId());
        }

        var waitingTask =
                awaitAuthorization(current, lease, approval.reason(), approval.createdAt());
        var approvalEntity = new HumanApprovalEntity();
        apply(approvalEntity, approval);
        apply(taskEntity, waitingTask);
        boardEntity.setBoard(waitingBoard);
        boardEntity.setFencingToken(lease.fencingToken());
        tasks.saveAndFlush(taskEntity);
        boards.saveAndFlush(boardEntity);
        approvals.saveAndFlush(approvalEntity);
        appendEventsAndOutbox(List.of(transition.event()), lease.fencingToken());
        return approval;
    }

    @Override
    @Transactional
    public ClarificationRequest requestClarification(ClarificationRequestTransition transition) {
        Objects.requireNonNull(transition, "transition 不能为空");
        var context = transition.parentContext();
        var lease = context.lease();
        leases.requireCurrent(lease);
        requireLeaseBoundary(context, lease);

        // 固定加锁与写入顺序：task → board → clarification → event → outbox。
        var taskEntity =
                tasks.findForUpdate(context.tenantId().value(), context.taskId().value())
                        .orElseThrow(() -> new IllegalStateException("澄清请求关联委托任务不存在"));
        var current = taskEntity.getTask();
        requireParentExecution(current, context, lease);
        var boardEntity =
                boards.findLockedByTenantIdAndTaskId(
                                context.tenantId().value(), context.taskId().value())
                        .orElseThrow(() -> new IllegalStateException("澄清请求关联 TaskBoard 不存在"));
        requireCurrentBoardFence(boardEntity, lease);
        var request = transition.clarification();
        if (clarifications.existsById(request.requestId())
                || clarifications
                        .findPendingForUpdate(context.tenantId().value(), context.taskId().value())
                        .isPresent()) {
            throw new IllegalStateException("任务已存在待处理澄清请求");
        }
        var waitingBoard =
                boardEntity
                        .getBoard()
                        .awaitClarification(request.executionId(), request.subTaskId());
        var waitingTask =
                awaitClarification(current, lease, request.requestId(), request.createdAt());
        var requestEntity = new ClarificationRequestEntity();
        apply(requestEntity, context.tenantId().value(), request);
        apply(taskEntity, waitingTask);
        boardEntity.setBoard(waitingBoard);
        boardEntity.setFencingToken(lease.fencingToken());
        tasks.saveAndFlush(taskEntity);
        boards.saveAndFlush(boardEntity);
        clarifications.saveAndFlush(requestEntity);
        appendEventsAndOutbox(List.of(transition.event()), lease.fencingToken());
        return request;
    }

    @Override
    @Transactional
    public Optional<InputCommit> consumeInputs(InputTransition transition) {
        Objects.requireNonNull(transition, "transition 不能为空");
        var context = transition.context();
        var lease = context.lease();
        leases.requireCurrent(lease);
        requireLeaseBoundary(context, lease);

        // 固定加锁与写入顺序：task → board → clarification → inputs → event → outbox。
        var taskEntity =
                tasks.findForUpdate(context.tenantId().value(), context.taskId().value())
                        .orElseThrow(() -> new IllegalStateException("输入关联委托任务不存在"));
        var current = taskEntity.getTask();
        requireParentExecutionOrWaiting(current, context, lease);
        var boardEntity =
                boards.findLockedByTenantIdAndTaskId(
                                context.tenantId().value(), context.taskId().value())
                        .orElseThrow(() -> new IllegalStateException("输入关联 TaskBoard 不存在"));
        requireInputBoardFence(boardEntity, current, lease);
        var clarificationEntity =
                clarifications
                        .findPendingForUpdate(context.tenantId().value(), context.taskId().value())
                        .orElse(null);
        var pending =
                inputs.findPendingForUpdate(context.tenantId().value(), context.taskId().value());
        if (pending.isEmpty()) return Optional.empty();
        var buffered = pending.stream().map(TaskInputEntity::getInput).toList();
        var cancel =
                buffered.stream()
                        .anyMatch(
                                input ->
                                        input.kind()
                                                == com.xuejiai.aaf.framework.intelligent.assistant
                                                        .model.ExecutionInput.Kind.CANCEL);
        var board = boardEntity.getBoard();
        var changedTask = current;
        ClarificationRequest changedClarification =
                clarificationEntity == null ? null : clarificationEntity.getRequest();
        var transitionEvents = new java.util.ArrayList<ExecutionEvent>();

        if (cancel) {
            if (changedClarification != null) {
                changedClarification = changedClarification.cancel(transition.at());
                board =
                        board.stopClarification(
                                changedClarification.executionId(),
                                changedClarification.subTaskId());
                apply(clarificationEntity, context.tenantId().value(), changedClarification);
                clarifications.save(clarificationEntity);
                transitionEvents.add(
                        clarificationStateEvent(
                                taskEntity.getCommand(),
                                changedClarification,
                                ExecutionEventType.CLARIFICATION_CANCELED,
                                ExecutionEventStatus.CANCELED,
                                transition.at()));
            } else {
                board = board.interruptRunning(false);
            }
            changedTask = cancelByInput(current, lease, transition.at());
        } else if (changedClarification != null) {
            changedClarification =
                    new InputBuffer(buffered)
                            .apply(changedClarification, transition.at())
                            .clarification();
            if (changedClarification.status() == ClarificationRequest.Status.RESOLVED) {
                board =
                        board.resumeAfterClarification(
                                changedClarification.executionId(),
                                changedClarification.subTaskId(),
                                changedClarification.values());
                taskEntity.setCommand(taskEntity.getCommand().asResume(transition.at()));
                changedTask = resume(current, lease, transition.at());
                transitionEvents.add(
                        clarificationStateEvent(
                                taskEntity.getCommand(),
                                changedClarification,
                                ExecutionEventType.CLARIFICATION_RESOLVED,
                                ExecutionEventStatus.RECOVERING,
                                transition.at()));
            } else if (changedClarification.status() == ClarificationRequest.Status.EXPIRED) {
                board =
                        board.stopClarification(
                                changedClarification.executionId(),
                                changedClarification.subTaskId());
                changedTask =
                        pause(current, lease, "clarification-deadline-reached", transition.at());
                transitionEvents.add(
                        clarificationStateEvent(
                                taskEntity.getCommand(),
                                changedClarification,
                                ExecutionEventType.CLARIFICATION_EXPIRED,
                                ExecutionEventStatus.PAUSED,
                                transition.at()));
            } else {
                transitionEvents.add(
                        clarificationStateEvent(
                                taskEntity.getCommand(),
                                changedClarification,
                                ExecutionEventType.CLARIFICATION_UPDATED,
                                ExecutionEventStatus.AWAITING_CLARIFICATION,
                                transition.at()));
            }
            apply(clarificationEntity, context.tenantId().value(), changedClarification);
            clarifications.save(clarificationEntity);
        }
        if (!cancel
                && changedTask.status() != Status.RUNNING
                && changedTask.status() != Status.PENDING) {
            changedTask = retainWaiting(changedTask, lease, transition.at());
        }

        var inputStatus =
                cancel
                        ? ExecutionEventStatus.CANCELED
                        : switch (changedTask.status()) {
                            case PENDING -> ExecutionEventStatus.RECOVERING;
                            case RUNNING -> ExecutionEventStatus.RUNNING;
                            case AWAITING_CLARIFICATION ->
                                    ExecutionEventStatus.AWAITING_CLARIFICATION;
                            case AWAITING_AUTHORIZATION ->
                                    ExecutionEventStatus.AWAITING_AUTHORIZATION;
                            case PAUSED -> ExecutionEventStatus.PAUSED;
                            case COMPLETED -> ExecutionEventStatus.COMPLETED;
                            case FAILED -> ExecutionEventStatus.FAILED;
                            case CANCELED -> ExecutionEventStatus.CANCELED;
                        };
        buffered.forEach(
                input ->
                        transitionEvents.add(
                                inputEvent(
                                        taskEntity.getCommand(),
                                        input,
                                        inputStatus,
                                        transition.at())));
        pending.forEach(entity -> entity.setConsumedAt(transition.at()));
        inputs.saveAll(pending);
        apply(taskEntity, changedTask);
        boardEntity.setBoard(board);
        boardEntity.setFencingToken(lease.fencingToken());
        tasks.saveAndFlush(taskEntity);
        boards.saveAndFlush(boardEntity);
        var committedEvents = appendEventsAndOutbox(transitionEvents, lease.fencingToken());
        return Optional.of(
                new InputCommit(changedTask, board, changedClarification, committedEvents));
    }

    @Override
    @Transactional
    public IterationCommit evaluateIteration(IterationEvaluationTransition transition) {
        Objects.requireNonNull(transition, "transition 不能为空");
        var context = transition.context();
        var lease = context.lease();
        leases.requireCurrent(lease);
        requireLeaseBoundary(context, lease);

        // 固定加锁与写入顺序：task → board → event → outbox。
        var taskEntity =
                tasks.findForUpdate(context.tenantId().value(), context.taskId().value())
                        .orElseThrow(() -> new IllegalStateException("迭代决策关联委托任务不存在"));
        var current = taskEntity.getTask();
        requireParentExecution(current, context, lease);
        var boardEntity =
                boards.findLockedByTenantIdAndTaskId(
                                context.tenantId().value(), context.taskId().value())
                        .orElseThrow(() -> new IllegalStateException("迭代决策关联 TaskBoard 不存在"));
        requireCurrentBoardFence(boardEntity, lease);
        var boundaryStop = iterationBoundary(current, transition.at());
        var board =
                boardEntity
                        .getBoard()
                        .evaluateIteration(
                                transition.evaluatorSubTaskId(),
                                transition.evaluation(),
                                boundaryStop,
                                transition.at());
        var stopped = board.goal().iteration().stopReason() != null;
        var changedTask =
                stopped
                        ? pause(
                                current,
                                lease,
                                "iteration-"
                                        + board.goal()
                                                .iteration()
                                                .stopReason()
                                                .name()
                                                .toLowerCase(java.util.Locale.ROOT),
                                transition.at())
                        : current;
        apply(taskEntity, changedTask);
        boardEntity.setBoard(board);
        boardEntity.setFencingToken(lease.fencingToken());
        tasks.saveAndFlush(taskEntity);
        boards.saveAndFlush(boardEntity);
        var transitionEvents = new java.util.ArrayList<ExecutionEvent>();
        transitionEvents.add(transition.event());
        if (stopped) {
            transitionEvents.add(
                    derivedEvent(
                            transition.event(),
                            ExecutionEventType.ITERATION_STOPPED,
                            ExecutionEventStatus.PAUSED,
                            Map.of(
                                    "groupId", board.goal().iteration().group().groupId(),
                                    "iteration", board.goal().iteration().currentIteration(),
                                    "stopReason", board.goal().iteration().stopReason().name()),
                            transition.at()));
        }
        var committedEvents = appendEventsAndOutbox(transitionEvents, lease.fencingToken());
        return new IterationCommit(changedTask, board, committedEvents.getFirst(), stopped);
    }

    @Override
    @Transactional
    public CommittedParent failOrRetry(ParentFailureTransition transition) {
        Objects.requireNonNull(transition, "transition 不能为空");
        var context = transition.context();
        var lease = context.lease();
        leases.requireCurrent(lease);
        requireLeaseBoundary(context, lease);

        // 固定加锁与写入顺序：task → board → event → outbox。
        var taskEntity =
                tasks.findForUpdate(context.tenantId().value(), context.taskId().value())
                        .orElseThrow(() -> new IllegalStateException("父失败关联委托任务不存在"));
        var current = taskEntity.getTask();
        requireParentExecution(current, context, lease);
        var boardEntity =
                boards.findLockedByTenantIdAndTaskId(
                                context.tenantId().value(), context.taskId().value())
                        .orElseThrow(() -> new IllegalStateException("父失败关联 TaskBoard 不存在"));
        requireCurrentBoardFence(boardEntity, lease);

        var failures = current.consecutiveFailures() + 1;
        var policy = current.contract().retryPolicy();
        var retry =
                current.attempts() < policy.maxAttempts()
                        && (!policy.retryTransientOnly() || transition.transientFailure())
                        && current.contract()
                                .deadline()
                                .isAfter(transition.at().plus(policy.initialBackoff()));
        var checkpoint = merge(current.checkpoint(), "lastFailure", transition.reason());
        final DelegatedTask changed;
        if (retry) {
            var nextAt =
                    transition
                            .at()
                            .plus(
                                    policy.initialBackoff()
                                            .multipliedBy(Math.max(1, current.attempts())));
            var nextExecution = new ExecutionId(randomId());
            var nextSession = new SessionId(randomId());
            var nextRun = new RunId(randomId());
            taskEntity.setCommand(
                    taskEntity
                            .getCommand()
                            .newExecution(
                                    nextExecution,
                                    nextSession,
                                    nextRun,
                                    context.lease(),
                                    transition.at()));
            changed =
                    copy(
                            current,
                            Status.PENDING,
                            new Owner(
                                    OwnerKind.ASSISTANT,
                                    taskEntity.getCommand().assistantId().value()),
                            current.budgetUsage(),
                            current.attempts(),
                            failures,
                            nextAt,
                            null,
                            null,
                            lease.fencingToken(),
                            checkpoint,
                            transition.at(),
                            nextSession,
                            nextExecution);
        } else {
            changed =
                    copy(
                            current,
                            Status.FAILED,
                            current.owner(),
                            current.budgetUsage(),
                            current.attempts(),
                            failures,
                            current.nextRunAt(),
                            null,
                            null,
                            lease.fencingToken(),
                            checkpoint,
                            transition.at(),
                            current.sessionId(),
                            current.executionId());
        }
        apply(taskEntity, changed);
        boardEntity.setBoard(boardEntity.getBoard().interruptRunning(retry));
        boardEntity.setFencingToken(lease.fencingToken());
        tasks.saveAndFlush(taskEntity);
        boards.saveAndFlush(boardEntity);
        var storedEvents = appendEventsAndOutbox(List.of(transition.event()), lease.fencingToken());
        return new CommittedParent(changed, storedEvents);
    }

    @Override
    @Transactional
    public CommittedParent commitParent(ParentStateTransition transition) {
        Objects.requireNonNull(transition, "transition 不能为空");
        var context = transition.context();
        var lease = context.lease();
        leases.requireCurrent(lease);
        requireLeaseBoundary(context, lease);

        // 固定加锁与写入顺序：task → board → event → outbox。
        var taskEntity =
                tasks.findForUpdate(context.tenantId().value(), context.taskId().value())
                        .orElseThrow(() -> new IllegalStateException("父状态变更关联委托任务不存在"));
        var current = taskEntity.getTask();
        requireParentExecution(current, context, lease);
        var boardEntity =
                boards.findLockedByTenantIdAndTaskId(
                                context.tenantId().value(), context.taskId().value())
                        .orElseThrow(() -> new IllegalStateException("父状态变更关联 TaskBoard 不存在"));
        requireCurrentBoardFence(boardEntity, lease);
        if (!boardEntity.getBoard().equals(transition.board())) {
            throw new IllegalStateException("父状态变更基于过期 TaskBoard");
        }

        var changed =
                switch (transition.status()) {
                    case COMPLETED ->
                            complete(current, lease, transition.result(), transition.at());
                    case FAILED -> fail(current, lease, transition.reason(), transition.at());
                    case PAUSED -> pause(current, lease, transition.reason(), transition.at());
                    default -> throw new IllegalArgumentException("未支持的父状态变更");
                };
        apply(taskEntity, changed);
        boardEntity.setBoard(transition.board());
        boardEntity.setFencingToken(lease.fencingToken());
        tasks.saveAndFlush(taskEntity);
        boards.saveAndFlush(boardEntity);
        var storedEvents = appendEventsAndOutbox(transition.events(), lease.fencingToken());
        return new CommittedParent(changed, storedEvents);
    }

    @Override
    @Transactional
    public HumanApproval decideAuthorization(AuthorizationDecision transition, Lease lease) {
        Objects.requireNonNull(transition, "transition 不能为空");
        leases.requireCurrent(lease);
        var requested = transition.approval();
        var context = requested.invocationContext();
        requireLeaseBoundary(context, lease);
        if (!lease.ownerId().equals("approval-transition-" + requested.approvalId())) {
            throw stale("授权决定 lease owner 与 approvalId 不一致");
        }

        // 固定加锁顺序：task → board → HITL/grant → event → outbox。
        var taskEntity =
                tasks.findForUpdate(context.tenantId().value(), context.taskId().value())
                        .orElseThrow(() -> new IllegalStateException("审批关联委托任务不存在"));
        var boardEntity =
                boards.findLockedByTenantIdAndTaskId(
                                context.tenantId().value(), context.taskId().value())
                        .orElseThrow(() -> new IllegalStateException("审批关联 TaskBoard 不存在"));
        var approvalEntity =
                approvals
                        .findForUpdate(context.tenantId().value(), requested.approvalId())
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "审批不存在: " + requested.approvalId()));
        var currentApproval = approvalEntity.getApproval();
        requireApprovalBoundary(currentApproval, requested);
        var currentTask = taskEntity.getTask();
        requireAuthorizationDecisionBoundary(
                currentTask,
                boardEntity,
                context,
                lease,
                currentApproval.status() == HumanApproval.Status.PENDING);
        if (currentApproval.status() != HumanApproval.Status.PENDING) {
            requireSameDecision(currentApproval, requested);
            return currentApproval;
        }

        var decidedAt = requested.decidedAt();
        if (requested.status() == HumanApproval.Status.APPROVED) {
            var grant = Objects.requireNonNull(transition.grant(), "批准决定缺少授权事实");
            taskEntity.setCommand(taskEntity.getCommand().asResume(decidedAt));
            apply(taskEntity, resume(currentTask, lease, decidedAt));
            boardEntity.setBoard(
                    boardEntity
                            .getBoard()
                            .resumeAfterAuthorization(context.executionId(), context.sessionId()));
            persistGrant(grant);
        } else {
            apply(taskEntity, pause(currentTask, lease, decidedAt));
            boardEntity.setBoard(boardEntity.getBoard().interruptRunning(false));
        }
        boardEntity.setFencingToken(lease.fencingToken());
        apply(approvalEntity, requested);
        tasks.saveAndFlush(taskEntity);
        boards.saveAndFlush(boardEntity);
        approvals.saveAndFlush(approvalEntity);
        appendEventsAndOutbox(List.of(transition.event()), lease.fencingToken());
        return requested;
    }

    @Override
    @Transactional
    public int publishOutbox(int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("transition outbox limit 必须在 1..100");
        }
        final List<TaskTransitionOutboxEntity> pending;
        try {
            pending = outbox.claimPending(limit);
        } catch (RuntimeException failure) {
            recordRelayFailure(OutboxRelayFailureType.CLAIM_FAILED);
            throw new OutboxRelayException(OutboxRelayFailureType.CLAIM_FAILED, failure);
        }
        var published = 0;
        for (var entity : pending) {
            final com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventStorePort
                            .StoredExecutionEvent
                    stored;
            try {
                stored = eventWriter.requireStored(entity.getEvent());
            } catch (RuntimeException ignored) {
                recordRelayFailure(OutboxRelayFailureType.EVENT_MISSING_OR_MISMATCHED);
                continue;
            }
            try {
                publisher.publishEvent(stored);
            } catch (RuntimeException ignored) {
                recordRelayFailure(OutboxRelayFailureType.DELIVERY_REJECTED);
                continue;
            }
            try {
                entity.setStatus("PUBLISHED");
                entity.setPublishedAt(Instant.now());
                outbox.save(entity);
                meters.counter(
                                "aaf.task.transition.outbox.relay",
                                "outcome",
                                "published",
                                "failure_type",
                                "none")
                        .increment();
                published++;
            } catch (RuntimeException failure) {
                recordRelayFailure(OutboxRelayFailureType.STATE_WRITE_FAILED);
                throw new OutboxRelayException(OutboxRelayFailureType.STATE_WRITE_FAILED, failure);
            }
        }
        outbox.flush();
        return published;
    }

    private void recordRelayFailure(OutboxRelayFailureType type) {
        meters.counter(
                        "aaf.task.transition.outbox.relay",
                        "outcome",
                        "failed",
                        "failure_type",
                        type.name().toLowerCase(java.util.Locale.ROOT))
                .increment();
    }

    private static IterationStopReason iterationBoundary(DelegatedTask task, Instant at) {
        if (!task.contract().deadline().isAfter(at)) {
            return IterationStopReason.DEADLINE_REACHED;
        }
        var usage = task.budgetUsage();
        var budget = task.contract().budget();
        if (usage.modelCalls() >= task.contract().maxModelCalls()
                || usage.modelTokens() >= budget.modelTokens()
                || usage.toolCalls() >= task.contract().maxToolCalls()
                || usage.toolUnits() >= budget.toolUnits()
                || usage.credits().compareTo(budget.credits()) >= 0) {
            return IterationStopReason.BUDGET_EXHAUSTED;
        }
        return null;
    }

    private List<ExecutionEvent> appendEventsAndOutbox(
            List<ExecutionEvent> events, long fencingToken) {
        var storedEvents =
                events.stream()
                        .map(event -> eventWriter.append(event, fencingToken).storedEvent().event())
                        .toList();
        storedEvents.forEach(this::appendOutbox);
        return storedEvents;
    }

    private void appendOutbox(ExecutionEvent stored) {
        var outboxId = "transition-" + stored.eventId().value();
        var existing = outbox.findById(outboxId).orElse(null);
        if (existing != null) {
            requireSameOutbox(existing, stored);
            return;
        }
        var entity = new TaskTransitionOutboxEntity();
        entity.setOutboxId(outboxId);
        entity.setTenantId(stored.tenantId().value());
        entity.setTaskId(stored.taskId().value());
        entity.setEventId(stored.eventId().value());
        entity.setStatus("PENDING");
        entity.setEvent(stored);
        entity.setCreatedAt(stored.createdAt());
        outbox.saveAndFlush(entity);
    }

    private void persistGrant(AuthorizationGrant grant) {
        var existing = grants.findById(grant.grantId()).orElse(null);
        if (existing != null) {
            if (!existing.getGrant().equals(grant)) {
                throw new IllegalStateException("grantId 已绑定不同授权事实: " + grant.grantId());
            }
            return;
        }
        var entity = new AuthorizationGrantEntity();
        entity.setGrantId(grant.grantId());
        entity.setTenantId(grant.tenantId().value());
        entity.setTaskId(grant.taskId().value());
        entity.setAction(grant.action());
        entity.setResource(grant.resource());
        entity.setExpiresAt(grant.expiresAt());
        entity.setRevokedAt(grant.revokedAt());
        entity.setGrant(grant);
        grants.save(entity);
    }

    private static DelegatedTask awaitClarification(
            DelegatedTask current, Lease lease, String requestId, Instant at) {
        return copy(
                current,
                Status.AWAITING_CLARIFICATION,
                current.owner(),
                current.budgetUsage(),
                current.attempts(),
                current.consecutiveFailures(),
                current.nextRunAt(),
                null,
                null,
                lease.fencingToken(),
                merge(current.checkpoint(), "clarificationRequestId", requestId),
                at,
                current.sessionId(),
                current.executionId());
    }

    private static DelegatedTask retainWaiting(DelegatedTask current, Lease lease, Instant at) {
        return copy(
                current,
                current.status(),
                current.owner(),
                current.budgetUsage(),
                current.attempts(),
                current.consecutiveFailures(),
                current.nextRunAt(),
                null,
                null,
                lease.fencingToken(),
                current.checkpoint(),
                at,
                current.sessionId(),
                current.executionId());
    }

    private static DelegatedTask cancelByInput(DelegatedTask current, Lease lease, Instant at) {
        return copy(
                current,
                Status.CANCELED,
                new Owner(OwnerKind.HUMAN, current.userId().value()),
                current.budgetUsage(),
                current.attempts(),
                current.consecutiveFailures(),
                current.nextRunAt(),
                null,
                null,
                lease.fencingToken(),
                merge(current.checkpoint(), "inputCanceled", true),
                at,
                current.sessionId(),
                current.executionId());
    }

    private static ExecutionEvent inputEvent(
            AssistantCommand command,
            com.xuejiai.aaf.framework.intelligent.assistant.model.ExecutionInput input,
            ExecutionEventStatus status,
            Instant at) {
        var type =
                switch (input.kind()) {
                    case CANCEL -> ExecutionEventType.INPUT_CANCELED;
                    case MODIFY -> ExecutionEventType.INPUT_MODIFIED;
                    case SUPPLEMENT -> ExecutionEventType.INPUT_SUPPLEMENTED;
                    case UNRELATED -> ExecutionEventType.INPUT_UNRELATED;
                };
        return new ExecutionEvent(
                new EventId("input-" + input.inputId()),
                command.tenantId(),
                command.conversationId(),
                command.sessionId(),
                command.taskId(),
                command.executionId(),
                command.runId(),
                command.parentExecutionId(),
                1,
                type,
                status,
                command.controlMode(),
                OwnerType.HUMAN,
                command.assistantId(),
                null,
                command.userId(),
                command.correlationId(),
                command.causationId(),
                command.idempotencyKey(),
                new ExecutionEventPayload(
                        Map.of(
                                "inputId", input.inputId(),
                                "kind", input.kind().name(),
                                "fieldCount", input.values().size())),
                at);
    }

    private static ExecutionEvent clarificationStateEvent(
            AssistantCommand command,
            ClarificationRequest clarification,
            ExecutionEventType type,
            ExecutionEventStatus status,
            Instant at) {
        return new ExecutionEvent(
                new EventId(
                        "clarification-"
                                + type.name().toLowerCase(java.util.Locale.ROOT)
                                + '-'
                                + clarification.requestId()),
                command.tenantId(),
                command.conversationId(),
                command.sessionId(),
                command.taskId(),
                command.executionId(),
                command.runId(),
                command.parentExecutionId(),
                1,
                type,
                status,
                command.controlMode(),
                OwnerType.HUMAN,
                command.assistantId(),
                null,
                command.userId(),
                command.correlationId(),
                command.causationId(),
                command.idempotencyKey(),
                new ExecutionEventPayload(
                        Map.of(
                                "requestId", clarification.requestId(),
                                "status", clarification.status().name(),
                                "completedFieldCount", clarification.values().size(),
                                "requiredFieldCount", clarification.requiredFields().size())),
                at);
    }

    private static ExecutionEvent derivedEvent(
            ExecutionEvent source,
            ExecutionEventType type,
            ExecutionEventStatus status,
            Map<String, Object> payload,
            Instant at) {
        return new ExecutionEvent(
                new EventId(
                        source.eventId().value()
                                + '-'
                                + type.name().toLowerCase(java.util.Locale.ROOT)),
                source.tenantId(),
                source.conversationId(),
                source.sessionId(),
                source.taskId(),
                source.executionId(),
                source.runId(),
                source.parentExecutionId(),
                1,
                type,
                status,
                source.controlMode(),
                source.ownerType(),
                source.assistantId(),
                source.agentId(),
                source.userId(),
                source.correlationId(),
                source.causationId(),
                source.idempotencyKey(),
                new ExecutionEventPayload(payload),
                at);
    }

    private static DelegatedTask awaitAuthorization(
            DelegatedTask current, Lease lease, String reason, Instant at) {
        return copy(
                current,
                Status.AWAITING_AUTHORIZATION,
                current.owner(),
                current.budgetUsage(),
                current.attempts(),
                current.consecutiveFailures(),
                current.nextRunAt(),
                null,
                null,
                lease.fencingToken(),
                merge(current.checkpoint(), "authorizationGap", reason),
                at,
                current.sessionId(),
                current.executionId());
    }

    private static DelegatedTask complete(
            DelegatedTask current, Lease lease, Map<String, Object> result, Instant at) {
        return copy(
                current,
                Status.COMPLETED,
                current.owner(),
                current.budgetUsage(),
                current.attempts(),
                0,
                current.nextRunAt(),
                null,
                null,
                lease.fencingToken(),
                result,
                at,
                current.sessionId(),
                current.executionId());
    }

    private static DelegatedTask fail(
            DelegatedTask current, Lease lease, String failure, Instant at) {
        return copy(
                current,
                Status.FAILED,
                current.owner(),
                current.budgetUsage(),
                current.attempts(),
                current.consecutiveFailures() + 1,
                current.nextRunAt(),
                null,
                null,
                lease.fencingToken(),
                merge(current.checkpoint(), "lastFailure", failure),
                at,
                current.sessionId(),
                current.executionId());
    }

    private static DelegatedTask pause(
            DelegatedTask current, Lease lease, String reason, Instant at) {
        return copy(
                current,
                Status.PAUSED,
                current.owner(),
                current.budgetUsage(),
                current.attempts(),
                current.consecutiveFailures(),
                current.nextRunAt(),
                null,
                null,
                lease.fencingToken(),
                merge(current.checkpoint(), "pauseReason", reason),
                at,
                current.sessionId(),
                current.executionId());
    }

    private static DelegatedTask resume(DelegatedTask current, Lease lease, Instant at) {
        return copy(
                current,
                Status.PENDING,
                current.owner(),
                current.budgetUsage(),
                current.attempts(),
                current.consecutiveFailures(),
                at,
                null,
                null,
                lease.fencingToken(),
                current.checkpoint(),
                at,
                current.sessionId(),
                current.executionId());
    }

    private static DelegatedTask pause(DelegatedTask current, Lease lease, Instant at) {
        return pause(current, lease, "用户拒绝工具授权", at);
    }

    private static void requireLeaseBoundary(
            com.xuejiai.aaf.framework.intelligent.agent.model.InvocationContext context,
            Lease lease) {
        if (!context.tenantId().equals(lease.tenantId())
                || !context.conversationId().equals(lease.conversationId())) {
            throw new IllegalStateException("conversation lease 与审批边界不一致");
        }
    }

    private static void requireParentExecutionOrWaiting(
            DelegatedTask task, InvocationContext context, Lease lease) {
        var active =
                task.status() == Status.RUNNING
                        && task.fencingToken() == lease.fencingToken()
                        && Objects.equals(task.leaseOwner(), lease.ownerId());
        var wakeable =
                (task.status() == Status.PENDING
                                || task.status() == Status.AWAITING_CLARIFICATION
                                || task.status() == Status.AWAITING_AUTHORIZATION
                                || task.status() == Status.PAUSED)
                        && task.leaseOwner() == null
                        && task.leaseUntil() == null
                        && lease.fencingToken() > task.fencingToken();
        if (!task.userId().equals(context.userId())
                || !task.conversationId().equals(context.conversationId())
                || !task.executionId().equals(context.executionId())
                || task.owner().kind() == OwnerKind.HUMAN
                || (!active && !wakeable)) {
            throw stale("旧 owner 或旧 execution 禁止消费输入");
        }
    }

    private static void requireInputBoardFence(
            TaskBoardEntity board, DelegatedTask task, Lease lease) {
        var boardFence = Objects.requireNonNull(board.getFencingToken(), "TaskBoard fence 不能为空");
        if (boardFence != task.fencingToken()
                || (task.status() == Status.RUNNING
                        ? boardFence != lease.fencingToken()
                        : boardFence >= lease.fencingToken())) {
            throw stale("输入消费的 TaskBoard fence 已过期");
        }
    }

    private static void requireParentExecution(
            DelegatedTask task, InvocationContext context, Lease lease) {
        if (!task.userId().equals(context.userId())
                || !task.conversationId().equals(context.conversationId())
                || !task.sessionId().equals(context.sessionId())
                || !task.executionId().equals(context.executionId())
                || task.status() != Status.RUNNING
                || task.owner().kind() == OwnerKind.HUMAN
                || task.fencingToken() != lease.fencingToken()
                || !Objects.equals(task.leaseOwner(), lease.ownerId())) {
            throw stale("旧 owner 或旧 execution 禁止提交父状态");
        }
    }

    private static void requireActiveChildExecution(
            DelegatedTask task, InvocationContext context, Lease lease) {
        if (!task.userId().equals(context.userId())
                || !task.conversationId().equals(context.conversationId())
                || !task.executionId().equals(context.parentExecutionId())
                || task.status() != Status.RUNNING
                || task.owner().kind() == OwnerKind.HUMAN
                || task.fencingToken() != lease.fencingToken()
                || !Objects.equals(task.leaseOwner(), lease.ownerId())
                || context.lease().fencingToken() != lease.fencingToken()
                || !Objects.equals(context.lease().ownerId(), lease.ownerId())) {
            throw stale("旧 owner、父 execution 或子 execution 禁止请求授权");
        }
    }

    private static void requireAuthorizationDecisionBoundary(
            DelegatedTask task,
            TaskBoardEntity boardEntity,
            InvocationContext context,
            Lease decisionLease,
            boolean pending) {
        if (!task.userId().equals(context.userId())
                || !task.conversationId().equals(context.conversationId())
                || !task.executionId().equals(context.parentExecutionId())
                || task.owner().kind() == OwnerKind.HUMAN
                || decisionLease.fencingToken() <= task.fencingToken()) {
            throw stale("过期审批禁止暂停或恢复当前 execution");
        }
        var boardFence =
                Objects.requireNonNull(boardEntity.getFencingToken(), "TaskBoard fence 不能为空");
        if (boardFence != task.fencingToken()) {
            throw stale("审批关联 TaskBoard 与父任务 fence 不一致");
        }
        var target =
                boardEntity.getBoard().subTasks().values().stream()
                        .filter(
                                subTask ->
                                        subTask.executionId().equals(context.executionId())
                                                && subTask.sessionId().equals(context.sessionId()))
                        .findFirst()
                        .orElseThrow(() -> stale("审批关联的当前子 execution/session 不存在"));
        if (pending
                && (task.status() != Status.AWAITING_AUTHORIZATION
                        || task.leaseOwner() != null
                        || task.leaseUntil() != null
                        || context.lease().fencingToken() != task.fencingToken()
                        || target.status()
                                != com.xuejiai.aaf.framework.intelligent.assistant.model.TaskBoard
                                        .Status.AWAITING_AUTHORIZATION)) {
            throw stale("审批关联任务已不处于原始等待授权边界");
        }
    }

    private static void requireCurrentBoardFence(TaskBoardEntity board, Lease lease) {
        if (board.getFencingToken() == null || board.getFencingToken() != lease.fencingToken()) {
            throw stale("旧 fencing token 不能提交 TaskBoard");
        }
    }

    private static DelegatedTaskPort.StaleExecutionException stale(String message) {
        return new DelegatedTaskPort.StaleExecutionException(message);
    }

    private static void requireApprovalBoundary(HumanApproval current, HumanApproval requested) {
        if (!current.approvalId().equals(requested.approvalId())
                || !current.invocationContext().equals(requested.invocationContext())
                || !current.action().equals(requested.action())
                || !current.resource().equals(requested.resource())) {
            throw new IllegalStateException("审批决定与持久审批事实边界不一致");
        }
    }

    private static void requireSameDecision(HumanApproval current, HumanApproval requested) {
        if (!current.equals(requested)) {
            throw new IllegalStateException("approvalId 已绑定不同决定事实");
        }
    }

    private static void requireSameOutbox(
            TaskTransitionOutboxEntity existing, ExecutionEvent requested) {
        if (!existing.getTenantId().equals(requested.tenantId().value())
                || !existing.getTaskId().equals(requested.taskId().value())
                || !existing.getEventId().equals(requested.eventId().value())
                || !existing.getEvent().equals(requested)) {
            throw new IllegalStateException("transition outboxId 已绑定不同事件事实");
        }
    }

    private static void apply(
            ClarificationRequestEntity entity, String tenantId, ClarificationRequest request) {
        entity.setRequestId(request.requestId());
        entity.setTenantId(tenantId);
        entity.setTaskId(request.taskId().value());
        entity.setExecutionId(request.executionId().value());
        entity.setStatus(request.status().name());
        entity.setDeadline(request.deadline());
        entity.setCreatedAt(request.createdAt());
        entity.setResolvedAt(request.resolvedAt());
        entity.setRequest(request);
    }

    private static void apply(HumanApprovalEntity entity, HumanApproval approval) {
        entity.setApprovalId(approval.approvalId());
        entity.setTenantId(approval.invocationContext().tenantId().value());
        entity.setTaskId(approval.invocationContext().taskId().value());
        entity.setStatus(approval.status().name());
        entity.setCreatedAt(approval.createdAt());
        entity.setDecidedAt(approval.decidedAt());
        entity.setApproval(approval);
    }

    private static void apply(DelegatedTaskEntity entity, DelegatedTask task) {
        entity.setTenantId(task.tenantId().value());
        entity.setUserId(task.userId().value());
        entity.setTaskId(task.taskId().value());
        entity.setConversationId(task.conversationId().value());
        entity.setSource(task.source().name());
        entity.setPriority(task.priority());
        entity.setExecutionId(task.executionId().value());
        entity.setStatus(task.status().name());
        entity.setOwnerKind(task.owner().kind().name());
        entity.setOwnerId(task.owner().ownerId());
        entity.setNextRunAt(task.nextRunAt());
        entity.setLeaseOwner(task.leaseOwner());
        entity.setLeaseUntil(task.leaseUntil());
        entity.setFencingToken(task.fencingToken());
        entity.setTask(task);
        entity.setCreatedAt(task.createdAt());
        entity.setUpdatedAt(task.updatedAt());
    }

    private static Map<String, Object> merge(Map<String, Object> source, String key, Object value) {
        var result = new LinkedHashMap<>(source);
        result.put(key, value == null ? "" : value);
        return Map.copyOf(result);
    }

    private static DelegatedTask copy(
            DelegatedTask source,
            Status status,
            Owner owner,
            BudgetUsage usage,
            int attempts,
            int failures,
            Instant nextRunAt,
            String leaseOwner,
            Instant leaseUntil,
            long fencingToken,
            Map<String, Object> checkpoint,
            Instant updatedAt,
            SessionId sessionId,
            ExecutionId executionId) {
        return new DelegatedTask(
                source.tenantId(),
                source.userId(),
                source.taskId(),
                source.conversationId(),
                sessionId,
                executionId,
                source.parentExecutionId(),
                source.source(),
                source.priority(),
                status,
                owner,
                source.contract(),
                usage,
                attempts,
                failures,
                nextRunAt,
                leaseOwner,
                leaseUntil,
                fencingToken,
                checkpoint,
                source.createdAt(),
                updatedAt);
    }

    private static String randomId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
