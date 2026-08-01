package com.xuejiai.aaf.framework.intelligent.assistant.application;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantVersion;
import com.xuejiai.aaf.framework.intelligent.assistant.model.CompletionCriteria;
import com.xuejiai.aaf.framework.intelligent.assistant.model.EffectiveContextManifest.SourceReference;
import com.xuejiai.aaf.framework.intelligent.assistant.model.ExecutionContract;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskBoard.SubTask;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskModelSelection;
import com.xuejiai.aaf.framework.intelligent.assistant.port.ConversationLeasePort.Lease;
import com.xuejiai.aaf.framework.intelligent.cognition.model.MemoryRecord.MemorySubject;
import com.xuejiai.aaf.framework.intelligent.cognition.model.MemoryRecord.SubjectKind;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ControlMode;
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
        AssistantVersion assistantVersion,
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
        List<SourceReference> contextCandidates,
        TaskModelSelection taskModelSelection,
        Instant requestedAt) {

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
        Objects.requireNonNull(assistantVersion, "assistantVersion 不能为空");
        Objects.requireNonNull(conversationId, "conversationId 不能为空");
        Objects.requireNonNull(sessionId, "sessionId 不能为空");
        Objects.requireNonNull(taskId, "taskId 不能为空");
        Objects.requireNonNull(executionId, "executionId 不能为空");
        Objects.requireNonNull(runId, "runId 不能为空");
        Objects.requireNonNull(correlationId, "correlationId 不能为空");
        Objects.requireNonNull(controlMode, "controlMode 不能为空");
        Objects.requireNonNull(taskModelSelection, "taskModelSelection 不能为空");
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
        if (controlMode == ControlMode.DELEGATED) {
            Objects.requireNonNull(executionContract, "DELEGATED 必须携带 ExecutionContract");
            executionContract.requireUsableAt(requestedAt);
            if (lease != null
                    && (!tenantId.equals(lease.tenantId())
                            || !conversationId.equals(lease.conversationId()))) {
                throw new IllegalArgumentException("委托命令与 conversation lease 边界不一致");
            }
        }
        if (operation.executesAgent()) {
            if (input == null || input.isBlank()) {
                throw new IllegalArgumentException("执行或恢复命令的 input 不能为空白");
            }
            Objects.requireNonNull(completionCriteria, "completionCriteria 不能为空");
        } else {
            input = input == null ? "" : input;
        }
    }

    public AssistantCommand asResume(Instant at) {
        return new AssistantCommand(
                Operation.RESUME,
                tenantId,
                userId,
                memorySubject,
                assistantId,
                assistantVersion,
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
                at);
    }

    public AssistantCommand withLease(Lease nextLease, Instant at) {
        return new AssistantCommand(
                operation,
                tenantId,
                userId,
                memorySubject,
                assistantId,
                assistantVersion,
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
                at);
    }

    public AssistantCommand newExecution(
            ExecutionId nextExecutionId,
            SessionId nextSessionId,
            RunId nextRunId,
            Lease nextLease,
            Instant at) {
        return new AssistantCommand(
                Operation.RESUME,
                tenantId,
                userId,
                memorySubject,
                assistantId,
                assistantVersion,
                conversationId,
                nextSessionId,
                taskId,
                nextExecutionId,
                nextRunId,
                executionId,
                correlationId,
                causationId,
                idempotencyKey,
                controlMode,
                executionContract,
                nextLease,
                0,
                input,
                completionCriteria,
                contextCandidates,
                taskModelSelection,
                at);
    }

    public AssistantCommand withInput(String nextInput, Lease nextLease, Instant at) {
        return new AssistantCommand(
                operation,
                tenantId,
                userId,
                memorySubject,
                assistantId,
                assistantVersion,
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
                nextInput,
                completionCriteria,
                contextCandidates,
                taskModelSelection,
                at);
    }

    public AssistantCommand forSubTask(SubTask subTask, Lease nextLease, Instant at) {
        Objects.requireNonNull(subTask, "subTask 不能为空");
        var idempotencyRoot =
                UUID.nameUUIDFromBytes(
                                (taskId.value() + '|' + subTask.subTaskId())
                                        .getBytes(StandardCharsets.UTF_8))
                        .toString();
        var childRunId =
                UUID.nameUUIDFromBytes(
                                (taskId.value()
                                                + '|'
                                                + subTask.subTaskId()
                                                + '|'
                                                + subTask.executionId().value())
                                        .getBytes(StandardCharsets.UTF_8))
                        .toString();
        return new AssistantCommand(
                Operation.SUBTASK,
                tenantId,
                userId,
                memorySubject,
                assistantId,
                assistantVersion,
                conversationId,
                subTask.sessionId(),
                taskId,
                subTask.executionId(),
                new RunId(childRunId),
                executionId,
                correlationId,
                new CausationId(executionId.value()),
                new IdempotencyKey(idempotencyRoot),
                controlMode,
                executionContract,
                nextLease,
                0,
                subTask.description(),
                completionCriteria,
                contextCandidates,
                taskModelSelection,
                at);
    }

    public enum Operation {
        START,
        RESUME,
        SUBTASK,
        PAUSE,
        CANCEL,
        TAKE_OVER;

        public boolean executesAgent() {
            return this == START || this == RESUME || this == SUBTASK;
        }
    }
}
