package com.xuejiai.aaf.framework.intelligent.assistant.application;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.xuejiai.aaf.framework.intelligent.assistant.model.CompletionCriteria;
import com.xuejiai.aaf.framework.intelligent.assistant.model.ExecutionContract;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskModelSelection;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskPlan.TaskNode;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskPlanDraft;
import com.xuejiai.aaf.framework.intelligent.assistant.port.ConversationLeasePort.Lease;
import com.xuejiai.aaf.framework.intelligent.cognition.model.MemoryRecord.MemorySubject;
import com.xuejiai.aaf.framework.intelligent.cognition.model.MemoryRecord.SubjectKind;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ControlMode;
import com.xuejiai.aaf.framework.intelligent.shared.event.NodeIdentity;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.AssistantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.CausationId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ConversationId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.CorrelationId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.IdempotencyKey;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.RunId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.SessionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;

/** 内置、自建和复制 Assistant 共用的命令。 */
public record AssistantCommand(
        Operation operation,
        TenantId tenantId,
        UserId userId,
        MemorySubject memorySubject,
        AssistantId assistantId,
        ConversationId conversationId,
        SessionId sessionId,
        TaskId taskId,
        ExecutionId executionId,
        RunId runId,
        ExecutionId parentExecutionId,
        CorrelationId correlationId,
        CausationId causationId,
        IdempotencyKey idempotencyKey,
        ControlMode controlMode,
        ExecutionContract executionContract,
        Lease lease,
        long sequenceBase,
        String input,
        CompletionCriteria completionCriteria,
        List<
                        com.xuejiai.aaf.framework.intelligent.assistant.model
                                .EffectiveContextManifest.SourceReference>
                contextCandidates,
        TaskModelSelection taskModelSelection,
        InvocationProfile invocationProfile,
        Instant requestedAt,
        NodeIdentity nodeIdentity,
        String dispatchId,
        long dispatchGeneration,
        long dispatchFencingToken,
        String dispatchLeaseOwner,
        Instant dispatchLeaseUntil,
        boolean resumeStateRequired) {

    public AssistantCommand(
            Operation operation,
            TenantId tenantId,
            UserId userId,
            MemorySubject memorySubject,
            AssistantId assistantId,
            ConversationId conversationId,
            SessionId sessionId,
            TaskId taskId,
            ExecutionId executionId,
            RunId runId,
            ExecutionId parentExecutionId,
            CorrelationId correlationId,
            CausationId causationId,
            IdempotencyKey idempotencyKey,
            ControlMode controlMode,
            ExecutionContract executionContract,
            Lease lease,
            long sequenceBase,
            String input,
            CompletionCriteria completionCriteria,
            List<
                            com.xuejiai.aaf.framework.intelligent.assistant.model
                                    .EffectiveContextManifest.SourceReference>
                    contextCandidates,
            TaskModelSelection taskModelSelection,
            InvocationProfile invocationProfile,
            Instant requestedAt,
            NodeIdentity nodeIdentity) {
        this(
                operation,
                tenantId,
                userId,
                memorySubject,
                assistantId,
                conversationId,
                sessionId,
                taskId,
                executionId,
                runId,
                parentExecutionId,
                correlationId,
                causationId,
                idempotencyKey,
                controlMode,
                executionContract,
                lease,
                sequenceBase,
                input,
                completionCriteria,
                contextCandidates,
                taskModelSelection,
                invocationProfile,
                requestedAt,
                nodeIdentity,
                null,
                0,
                0,
                null,
                null,
                false);
    }

    /**
     * 无编排节点身份的命令：START / RESUME 等 Assistant 自身发起的操作不属于任何板上节点。
     *
     * <p>{@code nodeIdentity == null} 是有意义的取值而非占位。执行者由 {@link #forNode} 派生，必须携带节点身份， 否则 AG-UI
     * 无法区分应答者与内部执行者、事件也无法按角色聚合。
     */
    public AssistantCommand(
            Operation operation,
            TenantId tenantId,
            UserId userId,
            MemorySubject memorySubject,
            AssistantId assistantId,
            ConversationId conversationId,
            SessionId sessionId,
            TaskId taskId,
            ExecutionId executionId,
            RunId runId,
            ExecutionId parentExecutionId,
            CorrelationId correlationId,
            CausationId causationId,
            IdempotencyKey idempotencyKey,
            ControlMode controlMode,
            ExecutionContract executionContract,
            Lease lease,
            long sequenceBase,
            String input,
            CompletionCriteria completionCriteria,
            List<
                            com.xuejiai.aaf.framework.intelligent.assistant.model
                                    .EffectiveContextManifest.SourceReference>
                    contextCandidates,
            TaskModelSelection taskModelSelection,
            InvocationProfile invocationProfile,
            Instant requestedAt) {
        this(
                operation,
                tenantId,
                userId,
                memorySubject,
                assistantId,
                conversationId,
                sessionId,
                taskId,
                executionId,
                runId,
                parentExecutionId,
                correlationId,
                causationId,
                idempotencyKey,
                controlMode,
                executionContract,
                lease,
                sequenceBase,
                input,
                completionCriteria,
                contextCandidates,
                taskModelSelection,
                invocationProfile,
                requestedAt,
                null);
    }

    public AssistantCommand {
        Objects.requireNonNull(operation, "operation 不能为空");
        Objects.requireNonNull(tenantId, "tenantId 不能为空");
        Objects.requireNonNull(userId, "userId 不能为空");
        Objects.requireNonNull(memorySubject, "memorySubject 不能为空");
        if (!tenantId.equals(memorySubject.tenantId())) {
            throw new IllegalArgumentException("memorySubject tenant 与命令 tenant 不一致");
        }
        if (memorySubject.kind() == SubjectKind.USER
                && !userId.value().equals(memorySubject.subjectId())) {
            throw new IllegalArgumentException("USER memorySubject 必须绑定当前 userId");
        }
        Objects.requireNonNull(assistantId, "assistantId 不能为空");
        Objects.requireNonNull(conversationId, "conversationId 不能为空");
        Objects.requireNonNull(sessionId, "sessionId 不能为空");
        Objects.requireNonNull(executionId, "executionId 不能为空");
        Objects.requireNonNull(runId, "runId 不能为空");
        Objects.requireNonNull(correlationId, "correlationId 不能为空");
        Objects.requireNonNull(controlMode, "controlMode 不能为空");
        if (taskId == null
                && (operation != Operation.START
                        || controlMode != ControlMode.READ_ONLY
                        || lease != null
                        || nodeIdentity != null)) {
            throw new IllegalArgumentException("无 Task 命令仅允许 READ_ONLY DIRECT START");
        }
        Objects.requireNonNull(taskModelSelection, "taskModelSelection 不能为空");
        Objects.requireNonNull(invocationProfile, "invocationProfile 不能为空");
        Objects.requireNonNull(requestedAt, "requestedAt 不能为空");
        contextCandidates =
                List.copyOf(Objects.requireNonNull(contextCandidates, "contextCandidates 不能为空"));
        if (sequenceBase < 0) {
            throw new IllegalArgumentException("sequenceBase 不能小于 0");
        }
        if (controlMode != ControlMode.READ_ONLY
                && controlMode != ControlMode.COLLABORATIVE
                && controlMode != ControlMode.DELEGATED) {
            throw new IllegalArgumentException("仅支持 READ_ONLY、COLLABORATIVE 和 DELEGATED");
        }
        if (controlMode == ControlMode.DELEGATED && executionContract == null) {
            throw new IllegalArgumentException("DELEGATED 必须携带 ExecutionContract");
        }
        if (executionContract != null) {
            executionContract.requireUsableAt(requestedAt);
        }
        if (lease != null
                && (!tenantId.equals(lease.tenantId())
                        || !conversationId.equals(lease.conversationId()))) {
            throw new IllegalArgumentException("持久命令与 conversation lease 边界不一致");
        }
        var hasDispatch = dispatchId != null;
        if (hasDispatch
                != (dispatchGeneration > 0
                        && dispatchFencingToken > 0
                        && dispatchLeaseOwner != null
                        && dispatchLeaseUntil != null)) {
            throw new IllegalArgumentException("dispatchId/generation/fence/lease 必须同时存在或同时缺省");
        }
        if (hasDispatch && (dispatchId.isBlank() || dispatchLeaseOwner.isBlank())) {
            throw new IllegalArgumentException("dispatch identity 不能为空白");
        }
        if (hasDispatch && !dispatchLeaseUntil.isAfter(requestedAt)) {
            throw new IllegalArgumentException("dispatch lease 必须在命令请求时间后有效");
        }
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("执行或恢复命令的 input 不能为空白");
        }
        Objects.requireNonNull(completionCriteria, "completionCriteria 不能为空");
    }

    public AssistantCommand promoteToTask(TaskId promotedTaskId, Instant at) {
        Objects.requireNonNull(promotedTaskId, "promotedTaskId 不能为空");
        Objects.requireNonNull(at, "at 不能为空");
        if (taskId != null
                || operation != Operation.START
                || controlMode != ControlMode.READ_ONLY
                || lease != null
                || nodeIdentity != null
                || dispatchId != null) {
            throw new IllegalStateException("仅无副作用的 DIRECT START 可派生 promotion command");
        }
        return new AssistantCommand(
                Operation.START,
                tenantId,
                userId,
                memorySubject,
                assistantId,
                conversationId,
                sessionId,
                promotedTaskId,
                executionId,
                runId,
                parentExecutionId,
                correlationId,
                causationId,
                idempotencyKey,
                controlMode,
                executionContract,
                null,
                sequenceBase,
                input,
                completionCriteria,
                contextCandidates,
                taskModelSelection,
                invocationProfile,
                at,
                null);
    }

    public AssistantCommand asResume(Instant at) {
        return copy(
                        operation == Operation.START ? Operation.RESUME : operation,
                        sessionId,
                        executionId,
                        runId,
                        parentExecutionId,
                        lease,
                        sequenceBase,
                        input,
                        taskModelSelection,
                        invocationProfile,
                        at)
                .withResumeStateRequired(true, at);
    }

    public AssistantCommand withResumeStateRequired(boolean required, Instant at) {
        return new AssistantCommand(
                operation,
                tenantId,
                userId,
                memorySubject,
                assistantId,
                conversationId,
                sessionId,
                taskId,
                executionId,
                runId,
                parentExecutionId,
                correlationId,
                causationId,
                idempotencyKey,
                controlMode,
                executionContract,
                lease,
                sequenceBase,
                input,
                completionCriteria,
                contextCandidates,
                taskModelSelection,
                invocationProfile,
                at,
                nodeIdentity,
                dispatchId,
                dispatchGeneration,
                dispatchFencingToken,
                dispatchLeaseOwner,
                dispatchLeaseUntil,
                required);
    }

    public AssistantCommand withLease(Lease nextLease, Instant at) {
        return copy(
                operation,
                sessionId,
                executionId,
                runId,
                parentExecutionId,
                nextLease,
                sequenceBase,
                input,
                taskModelSelection,
                invocationProfile,
                at);
    }

    public AssistantCommand withDispatch(
            Lease conversationLease,
            String claimedDispatchId,
            long generation,
            long fencingToken,
            String leaseOwner,
            Instant leaseUntil,
            Instant at) {
        return new AssistantCommand(
                operation,
                tenantId,
                userId,
                memorySubject,
                assistantId,
                conversationId,
                sessionId,
                taskId,
                executionId,
                runId,
                parentExecutionId,
                correlationId,
                causationId,
                idempotencyKey,
                controlMode,
                executionContract,
                conversationLease,
                sequenceBase,
                input,
                completionCriteria,
                contextCandidates,
                taskModelSelection,
                invocationProfile,
                at,
                nodeIdentity,
                claimedDispatchId,
                generation,
                fencingToken,
                leaseOwner,
                leaseUntil,
                resumeStateRequired);
    }

    public AssistantCommand withoutDispatch(Lease nextLease, Instant at) {
        return new AssistantCommand(
                operation,
                tenantId,
                userId,
                memorySubject,
                assistantId,
                conversationId,
                sessionId,
                taskId,
                executionId,
                runId,
                parentExecutionId,
                correlationId,
                causationId,
                idempotencyKey,
                controlMode,
                executionContract,
                nextLease,
                sequenceBase,
                input,
                completionCriteria,
                contextCandidates,
                taskModelSelection,
                invocationProfile,
                at,
                nodeIdentity,
                null,
                0,
                0,
                null,
                null,
                false);
    }

    public AssistantCommand newExecution(
            ExecutionId nextExecutionId,
            SessionId nextSessionId,
            RunId nextRunId,
            Lease nextLease,
            Instant at) {
        return copy(
                        Operation.RESUME,
                        nextSessionId,
                        nextExecutionId,
                        nextRunId,
                        parentExecutionId,
                        nextLease,
                        0,
                        input,
                        taskModelSelection,
                        invocationProfile,
                        at)
                .withResumeStateRequired(false, at);
    }

    public AssistantCommand withInput(String nextInput, Lease nextLease, Instant at) {
        return copy(
                operation,
                sessionId,
                executionId,
                runId,
                parentExecutionId,
                nextLease,
                sequenceBase,
                nextInput,
                taskModelSelection,
                invocationProfile,
                at);
    }

    public AssistantCommand forNode(
            TaskNode subTask,
            String resolvedInput,
            Lease nextLease,
            Instant at,
            TaskPlanDraft.AggregationContract.Kind aggregationKind) {
        return forNode(subTask, resolvedInput, nextLease, at, aggregationKind, controlMode);
    }

    /**
     * 与 {@link #forNode(TaskNode, String, Lease, Instant, TaskPlanDraft.AggregationContract.Kind)}
     * 相同，但允许为本次子执行显式覆盖 {@code controlMode}（ADR-006）。
     *
     * <p>用于 EXECUTOR 的 planning execution：它必须跑在 {@code READ_ONLY} 以复用 AAF 已有的工具可见性门控（{@code
     * DefaultToolGateway} 对非只读工具的强制拒绝），而任务整体的 {@code controlMode} 可能是 {@code COLLABORATIVE}/{@code
     * DELEGATED}。真正执行阶段（{@code executeApprovedPlan}）仍用任务原始 {@code controlMode}，不传覆盖值即可。
     */
    public AssistantCommand forNode(
            TaskNode subTask,
            String resolvedInput,
            Lease nextLease,
            Instant at,
            TaskPlanDraft.AggregationContract.Kind aggregationKind,
            ControlMode controlModeOverride) {
        Objects.requireNonNull(subTask, "subTask 不能为空");
        Objects.requireNonNull(controlModeOverride, "controlModeOverride 不能为空");
        if (resolvedInput == null || resolvedInput.isBlank()) {
            throw new IllegalArgumentException("resolvedInput 不能为空白");
        }
        var idempotencyRoot =
                UUID.nameUUIDFromBytes(
                                (taskId.value() + '|' + subTask.nodeId())
                                        .getBytes(StandardCharsets.UTF_8))
                        .toString();
        var childRunId =
                UUID.nameUUIDFromBytes(
                                (taskId.value()
                                                + '|'
                                                + subTask.nodeId()
                                                + '|'
                                                + subTask.executionId().value())
                                        .getBytes(StandardCharsets.UTF_8))
                        .toString();
        var baseChildProfile =
                switch (subTask.kind()) {
                    case COORDINATOR -> invocationProfile.forCoordinator(subTask.nodeId());
                    case AGGREGATOR -> invocationProfile.forAggregator(subTask.nodeId());
                    case EXECUTOR, EVALUATOR -> invocationProfile.forExecutor(subTask.nodeId());
                };
        var target = subTask.assistantTarget();
        var childProfile =
                target == null
                        ? baseChildProfile
                        : baseChildProfile.forAssistantTarget(
                                target.assistantRevision(),
                                target.roleKey(),
                                target.skillKey(),
                                target.allowedToolKeys());
        var childAssistantId = target == null ? assistantId : new AssistantId(target.assistantId());
        var childParentExecutionId =
                operation == Operation.START
                        ? null
                        : (nodeIdentity != null && nodeIdentity.nodeId().equals(subTask.nodeId())
                                ? parentExecutionId
                                : executionId);
        return new AssistantCommand(
                Operation.SUBTASK,
                tenantId,
                userId,
                memorySubject,
                childAssistantId,
                conversationId,
                subTask.sessionId(),
                taskId,
                subTask.executionId(),
                new RunId(childRunId),
                childParentExecutionId,
                correlationId,
                new CausationId(executionId.value()),
                new IdempotencyKey(idempotencyRoot),
                controlModeOverride,
                executionContract,
                nextLease,
                0,
                resolvedInput,
                completionCriteria,
                contextCandidates,
                subTask.modelSelection(),
                childProfile,
                at,
                nodeIdentityOf(subTask));
    }

    private AssistantCommand copy(
            Operation nextOperation,
            SessionId nextSessionId,
            ExecutionId nextExecutionId,
            RunId nextRunId,
            ExecutionId nextParentExecutionId,
            Lease nextLease,
            long nextSequenceBase,
            String nextInput,
            TaskModelSelection nextModelSelection,
            InvocationProfile nextInvocationProfile,
            Instant at) {
        return new AssistantCommand(
                nextOperation,
                tenantId,
                userId,
                memorySubject,
                assistantId,
                conversationId,
                nextSessionId,
                taskId,
                nextExecutionId,
                nextRunId,
                nextParentExecutionId,
                correlationId,
                causationId,
                idempotencyKey,
                controlMode,
                executionContract,
                nextLease,
                nextSequenceBase,
                nextInput,
                completionCriteria,
                contextCandidates,
                nextModelSelection,
                nextInvocationProfile,
                at,
                // 派生命令必须保留节点与 dispatch 身份，恢复只能显式创建新 generation/fence。
                nodeIdentity,
                dispatchId,
                dispatchGeneration,
                dispatchFencingToken,
                dispatchLeaseOwner,
                dispatchLeaseUntil,
                resumeStateRequired);
    }

    /**
     * 从板上节点派生事件身份。
     *
     * <p>在此处映射 {@code TaskPlan.Kind} → {@code NodeIdentity.NodeKind}，是为了让 {@code shared/event}
     * 不反向依赖 编排层——映射责任归产生该身份的一方。{@code nodeId} 在动态分解模式下由协调者命名，`NodeIdentity` 的构造器会 校验它是安全键（不含斜杠等会破坏
     * AG-UI source 路径的字符）。
     */
    private static NodeIdentity nodeIdentityOf(TaskNode subTask) {
        var kind =
                switch (subTask.kind()) {
                    case COORDINATOR -> NodeIdentity.NodeKind.COORDINATOR;
                    case EXECUTOR -> NodeIdentity.NodeKind.EXECUTOR;
                    case EVALUATOR -> NodeIdentity.NodeKind.EVALUATOR;
                    case AGGREGATOR -> NodeIdentity.NodeKind.AGGREGATOR;
                };
        // 正式执行计划统一由 AGGREGATOR finalizer 交付；coordinator-only planning run 始终是内部执行。
        var delivery = kind == NodeIdentity.NodeKind.AGGREGATOR;
        return new NodeIdentity(
                subTask.nodeId(), kind, subTask.roleKey(), subTask.skillKey(), delivery);
    }

    public enum Operation {
        START,
        RESUME,
        SUBTASK
    }
}
