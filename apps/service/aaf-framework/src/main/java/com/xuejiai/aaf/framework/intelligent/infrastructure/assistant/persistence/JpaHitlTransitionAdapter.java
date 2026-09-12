package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.framework.intelligent.agent.model.AuthorizationGrant;
import com.xuejiai.aaf.framework.intelligent.agent.model.InvocationContext;
import com.xuejiai.aaf.framework.intelligent.assistant.application.AssistantCommand;
import com.xuejiai.aaf.framework.intelligent.assistant.model.ClarificationRequest;
import com.xuejiai.aaf.framework.intelligent.assistant.model.Execution;
import com.xuejiai.aaf.framework.intelligent.assistant.model.ExecutionInput;
import com.xuejiai.aaf.framework.intelligent.assistant.model.HitlTransition.AuthorizationDecision;
import com.xuejiai.aaf.framework.intelligent.assistant.model.HitlTransition.AuthorizationRequestTransition;
import com.xuejiai.aaf.framework.intelligent.assistant.model.HitlTransition.ClarificationRequestTransition;
import com.xuejiai.aaf.framework.intelligent.assistant.model.HumanApproval;
import com.xuejiai.aaf.framework.intelligent.assistant.model.Task;
import com.xuejiai.aaf.framework.intelligent.assistant.model.Task.OwnerKind;
import com.xuejiai.aaf.framework.intelligent.assistant.model.Task.Status;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskDispatch;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskPlan;
import com.xuejiai.aaf.framework.intelligent.assistant.port.ConversationLeasePort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.ConversationLeasePort.Lease;
import com.xuejiai.aaf.framework.intelligent.assistant.port.HitlTransitionPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.HitlTransitionPort.ClarificationCommit;
import com.xuejiai.aaf.framework.intelligent.assistant.port.HitlTransitionPort.OutboxRelayException;
import com.xuejiai.aaf.framework.intelligent.assistant.port.HitlTransitionPort.OutboxRelayFailureType;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskPlanPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskUnitOfWork;
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

import io.micrometer.core.instrument.MeterRegistry;

/** PostgreSQL HITL governance facts、canonical Task/Execution/Plan 与 outbox 的原子提交适配器。 */
public class JpaHitlTransitionAdapter implements HitlTransitionPort {
    private final TaskRootRepository taskRoots;
    private final TaskExecutionRepository taskExecutions;
    private final TaskDispatchRepository taskDispatches;
    private final TaskPlanPort boards;
    private final HumanApprovalRepository approvals;
    private final AuthorizationGrantRepository grants;
    private final ClarificationRequestRepository clarifications;
    private final TaskInputRepository inputs;
    private final SynchronousExecutionEventWriter eventWriter;
    private final TaskTransitionOutboxRepository outbox;
    private final ApplicationEventPublisher publisher;
    private final ConversationLeasePort leases;
    private final MeterRegistry meters;

    public JpaHitlTransitionAdapter(
            TaskRootRepository taskRoots,
            TaskExecutionRepository taskExecutions,
            TaskDispatchRepository taskDispatches,
            TaskPlanPort boards,
            HumanApprovalRepository approvals,
            AuthorizationGrantRepository grants,
            ClarificationRequestRepository clarifications,
            TaskInputRepository inputs,
            SynchronousExecutionEventWriter eventWriter,
            TaskTransitionOutboxRepository outbox,
            ApplicationEventPublisher publisher,
            ConversationLeasePort leases,
            MeterRegistry meters) {
        this.taskRoots = Objects.requireNonNull(taskRoots, "taskRoots 不能为空");
        this.taskExecutions = Objects.requireNonNull(taskExecutions, "taskExecutions 不能为空");
        this.taskDispatches = Objects.requireNonNull(taskDispatches, "taskDispatches 不能为空");
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
    public HumanApproval requestAuthorization(AuthorizationRequestTransition transition) {
        Objects.requireNonNull(transition, "transition 不能为空");
        var approval = transition.approval();
        var context = approval.invocationContext();
        var lease = context.lease();
        leases.requireCurrent(lease);
        requireLeaseBoundary(context, lease);

        var taskEntity =
                taskRoots
                        .findForUpdate(context.tenantId().value(), context.taskId().value())
                        .orElseThrow(() -> new IllegalStateException("授权请求关联 Task 不存在"));
        var executionEntity =
                taskExecutions
                        .findForUpdate(context.tenantId().value(), context.executionId().value())
                        .orElseThrow(() -> new IllegalStateException("授权请求关联 Execution 不存在"));
        var dispatchEntity =
                taskDispatches
                        .findActiveForUpdate(
                                context.tenantId().value(), context.executionId().value())
                        .orElseThrow(() -> new IllegalStateException("授权请求关联 active Dispatch 不存在"));
        var task = taskEntity.getTask();
        var execution = executionEntity.getExecution();
        var dispatch = dispatchEntity.getDispatch();
        requirePlannedHitlBoundary(context, task, execution, dispatch, approval.createdAt());
        var currentPlan =
                boards.find(context.tenantId(), context.taskId())
                        .orElseThrow(() -> new IllegalStateException("授权请求关联 TaskPlan 不存在"));
        var waitingPlan =
                currentPlan.awaitAuthorization(context.executionId(), context.sessionId());
        if (approvals.existsById(approval.approvalId())) {
            throw new IllegalStateException("approvalId 已存在: " + approval.approvalId());
        }

        taskEntity.setTask(
                task.withRuntime(
                        Status.AWAITING_AUTHORIZATION,
                        task.owner(),
                        task.budgetUsage(),
                        task.checkpoint().withAnnotation("approvalId", approval.approvalId()),
                        task.recoveryPoint(),
                        approval.createdAt()));
        executionEntity.setExecution(
                execution.withStatus(
                        Execution.Status.AWAITING_AUTHORIZATION, approval.createdAt()));
        dispatchEntity.setDispatch(closeDispatch(dispatch, approval.createdAt()));
        var approvalEntity = new HumanApprovalEntity();
        apply(approvalEntity, approval);
        taskRoots.save(taskEntity);
        taskExecutions.save(executionEntity);
        taskDispatches.save(dispatchEntity);
        boards.update(context.tenantId(), waitingPlan, lease);
        approvals.saveAndFlush(approvalEntity);
        appendEventsAndOutbox(List.of(transition.event()), dispatch.fencingToken());
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
        var request = transition.clarification();

        var taskEntity =
                taskRoots
                        .findForUpdate(context.tenantId().value(), context.taskId().value())
                        .orElseThrow(() -> new IllegalStateException("澄清请求关联 Task 不存在"));
        var executionEntity =
                taskExecutions
                        .findForUpdate(context.tenantId().value(), context.executionId().value())
                        .orElseThrow(() -> new IllegalStateException("澄清请求关联 Execution 不存在"));
        var dispatchEntity =
                taskDispatches
                        .findActiveForUpdate(
                                context.tenantId().value(), context.executionId().value())
                        .orElseThrow(() -> new IllegalStateException("澄清请求关联 active Dispatch 不存在"));
        var task = taskEntity.getTask();
        var execution = executionEntity.getExecution();
        var dispatch = dispatchEntity.getDispatch();
        requirePlannedHitlBoundary(context, task, execution, dispatch, request.createdAt());
        if (!request.executionId().equals(execution.executionId())
                || !request.nodeId().equals(execution.nodeId())) {
            throw stale("ClarificationRequest 未绑定当前 node execution");
        }
        var currentPlan =
                boards.find(context.tenantId(), context.taskId())
                        .orElseThrow(() -> new IllegalStateException("澄清请求关联 TaskPlan 不存在"));
        if (clarifications.existsById(request.requestId())
                || clarifications
                        .findPendingForUpdate(context.tenantId().value(), context.taskId().value())
                        .isPresent()) {
            throw new IllegalStateException("任务已存在待处理澄清请求");
        }
        var waitingPlan = currentPlan.awaitClarification(request.executionId(), request.nodeId());
        taskEntity.setTask(
                task.withRuntime(
                        Status.AWAITING_CLARIFICATION,
                        task.owner(),
                        task.budgetUsage(),
                        task.checkpoint()
                                .withAnnotation("clarificationRequestId", request.requestId()),
                        task.recoveryPoint(),
                        request.createdAt()));
        executionEntity.setExecution(
                execution.withStatus(Execution.Status.AWAITING_CLARIFICATION, request.createdAt()));
        dispatchEntity.setDispatch(closeDispatch(dispatch, request.createdAt()));
        var requestEntity = new ClarificationRequestEntity();
        apply(requestEntity, context.tenantId().value(), request);
        taskRoots.save(taskEntity);
        taskExecutions.save(executionEntity);
        taskDispatches.save(dispatchEntity);
        boards.update(context.tenantId(), waitingPlan, lease);
        clarifications.saveAndFlush(requestEntity);
        appendEventsAndOutbox(List.of(transition.event()), dispatch.fencingToken());
        return request;
    }

    @Override
    @Transactional
    public ClarificationCommit resumeClarification(ExecutionInput input, Lease lease) {
        Objects.requireNonNull(input, "input 不能为空");
        Objects.requireNonNull(lease, "lease 不能为空");
        if (input.kind() != ExecutionInput.Kind.SUPPLEMENT || input.requestId() == null) {
            throw new IllegalArgumentException("clarification resume 仅接受绑定 requestId 的 SUPPLEMENT");
        }
        leases.requireCurrent(lease);
        var taskEntity =
                taskRoots
                        .findForUpdate(input.tenantId().value(), input.taskId().value())
                        .orElseThrow(() -> new IllegalStateException("澄清输入关联 Task 不存在"));
        var task = taskEntity.getTask();
        if (!task.userId().equals(input.userId())
                || !task.conversationId().equals(lease.conversationId())
                || !task.tenantId().equals(lease.tenantId())) {
            throw stale("澄清输入不属于当前用户或 conversation lease");
        }
        var requestEntity =
                clarifications
                        .findPendingForUpdate(input.tenantId().value(), input.taskId().value())
                        .orElse(null);
        if (requestEntity == null) {
            var completed =
                    clarifications
                            .findByRequestId(input.tenantId().value(), input.requestId())
                            .map(ClarificationRequestEntity::getRequest)
                            .orElseThrow(() -> stale("Task 当前没有关联 clarification"));
            if (completed.status() != ClarificationRequest.Status.RESOLVED
                    || input.values().entrySet().stream()
                            .anyMatch(
                                    entry ->
                                            !entry.getValue()
                                                    .equals(
                                                            completed
                                                                    .values()
                                                                    .get(entry.getKey())))) {
                throw stale("requestId 已终止或绑定不同输入事实");
            }
            return new ClarificationCommit(task, completed, completed.executionId(), false);
        }
        var request = requestEntity.getRequest();
        if (!request.requestId().equals(input.requestId())) {
            throw stale("requestId 已过期或不属于当前 Task");
        }
        var executionEntity =
                taskExecutions
                        .findForUpdate(input.tenantId().value(), request.executionId().value())
                        .orElseThrow(() -> stale("clarification 关联 Execution 不存在"));
        var execution = executionEntity.getExecution();
        var plan =
                boards.find(input.tenantId(), input.taskId())
                        .orElseThrow(() -> stale("clarification 关联 current TaskPlan 不存在"));
        var node = plan.nodes().get(request.nodeId());
        if (task.status() != Status.AWAITING_CLARIFICATION
                || !Objects.equals(task.currentPlanId(), execution.planId())
                || !Objects.equals(task.currentPlanRevision(), execution.planRevision())
                || execution.status() != Execution.Status.AWAITING_CLARIFICATION
                || node == null
                || node.status() != TaskPlan.Status.AWAITING_CLARIFICATION
                || !node.executionId().equals(execution.executionId())
                || !node.sessionId().equals(execution.sessionId())
                || node.attempts() != execution.attemptNo()) {
            throw stale("clarification 未绑定当前 task/plan/node/execution/attempt");
        }
        var inputEntity =
                inputs
                        .findPendingForUpdate(input.tenantId().value(), input.taskId().value())
                        .stream()
                        .filter(candidate -> candidate.getInputId().equals(input.inputId()))
                        .findFirst()
                        .orElseThrow(() -> stale("clarification input 已消费或不存在"));
        if (!inputEntity.getInput().equals(input)) {
            throw stale("inputId 已绑定不同输入事实");
        }
        var changedRequest = request.apply(input.values(), false);
        var resumed = changedRequest.complete();
        if (resumed) {
            changedRequest = changedRequest.resolve(input.receivedAt());
            plan =
                    plan.resumeAfterClarification(
                            execution.executionId(), execution.nodeId(), changedRequest.values());
            task =
                    task.withRuntime(
                            Status.READY,
                            task.owner(),
                            task.budgetUsage(),
                            task.checkpoint(),
                            task.recoveryPoint(),
                            input.receivedAt());
            execution = execution.withStatus(Execution.Status.DISPATCHED, input.receivedAt());
            taskEntity.setTask(task);
            executionEntity.setExecution(execution);
            boards.update(input.tenantId(), plan, lease);
            taskRoots.save(taskEntity);
            taskExecutions.save(executionEntity);
        }
        apply(requestEntity, input.tenantId().value(), changedRequest);
        inputEntity.setConsumedAt(input.receivedAt());
        clarifications.save(requestEntity);
        inputs.save(inputEntity);
        var previousDispatch =
                taskDispatches
                        .findFirstByTenantIdAndExecutionIdOrderByIdDesc(
                                input.tenantId().value(), execution.executionId().value())
                        .orElseThrow(() -> stale("clarification 关联 Dispatch 不存在"))
                        .getDispatch();
        var event =
                clarificationStateEvent(
                        executionEntity.getCommand(),
                        changedRequest,
                        resumed
                                ? ExecutionEventType.CLARIFICATION_RESOLVED
                                : ExecutionEventType.CLARIFICATION_UPDATED,
                        resumed
                                ? ExecutionEventStatus.RECOVERING
                                : ExecutionEventStatus.AWAITING_CLARIFICATION,
                        input.receivedAt());
        appendEventsAndOutbox(List.of(event), previousDispatch.fencingToken());
        return new ClarificationCommit(task, changedRequest, execution.executionId(), resumed);
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

        var taskEntity =
                taskRoots
                        .findForUpdate(context.tenantId().value(), context.taskId().value())
                        .orElseThrow(() -> new IllegalStateException("审批关联 Task 不存在"));
        var executionEntity =
                taskExecutions
                        .findForUpdate(context.tenantId().value(), context.executionId().value())
                        .orElseThrow(() -> new IllegalStateException("审批关联 Execution 不存在"));
        var currentPlan =
                boards.find(context.tenantId(), context.taskId())
                        .orElseThrow(() -> new IllegalStateException("审批关联 TaskPlan 不存在"));
        var approvalEntity =
                approvals
                        .findForUpdate(context.tenantId().value(), requested.approvalId())
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "审批不存在: " + requested.approvalId()));
        var currentApproval = approvalEntity.getApproval();
        requireApprovalBoundary(currentApproval, requested);
        var task = taskEntity.getTask();
        var execution = executionEntity.getExecution();
        requireWaitingAuthorizationBoundary(task, currentPlan, execution, context);
        if (currentApproval.status() != HumanApproval.Status.PENDING) {
            requireSameDecision(currentApproval, requested);
            return currentApproval;
        }

        var decidedAt = Objects.requireNonNull(requested.decidedAt(), "授权决定缺少 decidedAt");
        if (requested.status() == HumanApproval.Status.APPROVED) {
            var grant = Objects.requireNonNull(transition.grant(), "批准决定缺少授权事实");
            taskEntity.setTask(
                    task.withRuntime(
                            Status.READY,
                            task.owner(),
                            task.budgetUsage(),
                            task.checkpoint(),
                            task.recoveryPoint(),
                            decidedAt));
            executionEntity.setExecution(
                    execution.withStatus(Execution.Status.DISPATCHED, decidedAt));
            currentPlan =
                    currentPlan.resumeAfterAuthorization(
                            context.executionId(), context.sessionId());
            persistGrant(grant);
        } else {
            taskEntity.setTask(
                    task.withRuntime(
                            Status.PAUSED,
                            task.owner(),
                            task.budgetUsage(),
                            task.checkpoint()
                                    .withAnnotation(
                                            "authorizationRejected", requested.approvalId()),
                            task.recoveryPoint(),
                            decidedAt));
            executionEntity.setExecution(execution.withStatus(Execution.Status.PAUSED, decidedAt));
            currentPlan = currentPlan.interruptRunning(false);
        }
        apply(approvalEntity, requested);
        taskRoots.save(taskEntity);
        taskExecutions.save(executionEntity);
        boards.update(context.tenantId(), currentPlan, lease);
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
        var existing = outbox.findByOutboxId(outboxId).orElse(null);
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
                at,
                command.nodeIdentity());
    }

    private static void requireWaitingAuthorizationBoundary(
            Task task, TaskPlan plan, Execution execution, InvocationContext context) {
        var node =
                plan.nodes().values().stream()
                        .filter(candidate -> candidate.nodeId().equals(execution.nodeId()))
                        .findFirst()
                        .orElseThrow(() -> stale("审批关联 node 不存在"));
        if (!task.tenantId().equals(context.tenantId())
                || !task.userId().equals(context.userId())
                || !task.conversationId().equals(context.conversationId())
                || !task.taskId().equals(context.taskId())
                || task.status() != Status.AWAITING_AUTHORIZATION
                || !Objects.equals(task.currentPlanId(), plan.planId())
                || !Objects.equals(task.currentPlanRevision(), plan.revision())
                || execution.scope() != Execution.Scope.TASK_NODE
                || !execution.executionId().equals(context.executionId())
                || !execution.sessionId().equals(context.sessionId())
                || execution.status() != Execution.Status.AWAITING_AUTHORIZATION
                || node.status() != TaskPlan.Status.AWAITING_AUTHORIZATION
                || !node.executionId().equals(execution.executionId())
                || !node.sessionId().equals(execution.sessionId())
                || node.attempts() != execution.attemptNo()) {
            throw stale("审批未绑定当前 task/plan/node/execution/attempt");
        }
    }

    private static void requirePlannedHitlBoundary(
            InvocationContext context,
            Task task,
            Execution execution,
            TaskDispatch dispatch,
            Instant at) {
        if (context.taskId() == null
                || context.nodeIdentity() == null
                || context.dispatchId() == null
                || !task.tenantId().equals(context.tenantId())
                || !task.userId().equals(context.userId())
                || !task.conversationId().equals(context.conversationId())
                || !task.taskId().equals(context.taskId())
                || !Objects.equals(task.currentPlanId(), execution.planId())
                || !Objects.equals(task.currentPlanRevision(), execution.planRevision())
                || execution.scope() != Execution.Scope.TASK_NODE
                || !execution.taskId().equals(context.taskId())
                || !execution.executionId().equals(context.executionId())
                || !execution.sessionId().equals(context.sessionId())
                || !execution.nodeId().equals(context.nodeIdentity().nodeId())
                || execution.status() != Execution.Status.RUNNING
                || task.status() != Status.RUNNING
                || task.owner().kind() == OwnerKind.HUMAN
                || !dispatch.accepts(
                        context.dispatchId(),
                        context.dispatchGeneration(),
                        context.dispatchFencingToken(),
                        context.dispatchLeaseOwner(),
                        at)) {
            throw stale("旧 plan/node/execution/attempt/dispatch 禁止进入 HITL");
        }
    }

    private static TaskDispatch closeDispatch(TaskDispatch dispatch, Instant at) {
        return new TaskDispatch(
                dispatch.dispatchId(),
                dispatch.tenantId(),
                dispatch.executionId(),
                TaskDispatch.Status.CANCELED,
                dispatch.nextRunAt(),
                null,
                null,
                dispatch.generation(),
                dispatch.fencingToken(),
                dispatch.deliveryAttempts(),
                dispatch.lastError(),
                dispatch.version(),
                dispatch.createdAt(),
                at);
    }

    private static void requireLeaseBoundary(
            com.xuejiai.aaf.framework.intelligent.agent.model.InvocationContext context,
            Lease lease) {
        if (!context.tenantId().equals(lease.tenantId())
                || !context.conversationId().equals(lease.conversationId())) {
            throw new IllegalStateException("conversation lease 与审批边界不一致");
        }
    }

    private static TaskUnitOfWork.StaleExecutionException stale(String message) {
        return new TaskUnitOfWork.StaleExecutionException(message);
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
}
