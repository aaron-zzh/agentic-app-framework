package com.xuejiai.aaf.module.ai.chat.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.enums.RiskLevel;
import com.xuejiai.aaf.framework.engine.task.CheckpointStore;
import com.xuejiai.aaf.framework.engine.task.TaskEventBus;
import com.xuejiai.aaf.framework.intelligent.assistant.application.AssistantCommand;
import com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantVersion;
import com.xuejiai.aaf.framework.intelligent.assistant.model.CompletionCriteria;
import com.xuejiai.aaf.framework.intelligent.assistant.port.AssistantCommandPort;
import com.xuejiai.aaf.framework.intelligent.cognition.model.MemoryRecord.MemorySubject;
import com.xuejiai.aaf.framework.intelligent.cognition.model.MemoryRecord.SubjectKind;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ControlMode;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventStorePort;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventType;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.AssistantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ConversationId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.CorrelationId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.IdempotencyKey;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.RunId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.SessionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;
import com.xuejiai.aaf.module.ai.chat.domain.ChatTask;
import com.xuejiai.aaf.module.ai.chat.domain.TaskCheckpoint;
import com.xuejiai.aaf.module.ai.chat.domain.TaskEvent;
import com.xuejiai.aaf.module.ai.chat.domain.TaskExecution;
import com.xuejiai.aaf.module.ai.chat.domain.enums.TaskExecutionStatus;
import com.xuejiai.aaf.module.ai.chat.repository.AiTaskExecutionRepository;
import com.xuejiai.aaf.module.ai.chat.repository.ChatTaskRepository;
import com.xuejiai.aaf.module.ai.output.domain.AiOutput;
import com.xuejiai.aaf.module.ai.output.domain.enums.OutputCategory;
import com.xuejiai.aaf.module.ai.output.domain.enums.OutputSourceType;
import com.xuejiai.aaf.module.ai.output.service.AiOutputService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 持久任务执行器——委托 framework 层组件实现可恢复、可观测、状态一致的长任务执行。
 *
 * <p>职责分工：本类负责 ChatTask 执行状态与业务产出持久化，实际 AI 执行统一委托 P2
 * {@link AssistantCommandPort}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DurableTaskExecutor {

    private final AiTaskExecutionRepository executionRepository;
    private final ChatTaskRepository taskRepository;
    private final CheckpointStore checkpointStore;
    private final TaskEventBus taskEventBus;
    private final ExecutionEventStorePort eventStore;
    private final AssistantCommandPort assistantCommandPort;
    private final ChatService chatService;
    private final AiOutputService aiOutputService;

    private static final int ORPHAN_TIMEOUT_MINUTES = 10;
    private static final String DEFAULT_ASSISTANT_ID = "system.assistant.content-creator";
    private static final AssistantVersion DEFAULT_ASSISTANT_VERSION = new AssistantVersion(1);

    // === 执行实例管理 ===

    /** 创建主执行实例 */
    @Transactional
    public TaskExecution createExecution(ChatTask task) {
        var existing = executionRepository.findFirstByTaskIdOrderByAttemptNoDesc(task.getId());
        int attemptNo = existing.map(e -> e.getAttemptNo() + 1).orElse(1);

        var execution = new TaskExecution();
        execution.setTaskId(task.getId());
        execution.setAttemptNo(attemptNo);
        execution.setStatus(TaskExecutionStatus.PENDING);
        executionRepository.save(execution);

        emitEvent(
                task.getId(),
                execution.getId(),
                null,
                "execution_created",
                "{\"attemptNo\":%d}".formatted(attemptNo));
        return execution;
    }

    /** 创建子执行实例（fork） */
    @Transactional
    public TaskExecution createSubExecution(
            Long taskId, Long parentExecutionId, String subtaskKey, String role) {
        var sub = new TaskExecution();
        sub.setTaskId(taskId);
        sub.setParentExecutionId(parentExecutionId);
        sub.setSubtaskKey(subtaskKey);
        sub.setRole(role);
        sub.setAttemptNo(1);
        sub.setStatus(TaskExecutionStatus.PENDING);
        executionRepository.save(sub);

        emitEvent(
                taskId,
                parentExecutionId,
                subtaskKey,
                "subtask_forked",
                "{\"role\":\"%s\",\"executionId\":%d}".formatted(role, sub.getId()));
        return sub;
    }

    /** CAS 抢占启动 */
    @Transactional
    public boolean tryStart(Long executionId) {
        return executionRepository.casStart(executionId) > 0;
    }

    // === 单任务执行（委托 AssistantCommandPort） ===

    /** 执行单个任务——通过 P2 Assistant 应用入口执行。 */
    public void execute(ChatTask task, TaskExecution execution) {
        emitEvent(
                task.getId(),
                execution.getId(),
                null,
                "task_started",
                "{\"title\":\"%s\"}".formatted(escapeJson(task.getTitle())));

        var startedAt = System.nanoTime();
        try {
            var input =
                    task.getDescription() != null
                            ? task.getTitle() + "\n" + task.getDescription()
                            : task.getTitle();
            var events =
                    assistantCommandPort
                            .execute(createCommand(task, execution, input))
                            .collectList()
                            .blockOptional()
                            .orElseThrow(() -> new IllegalStateException("助理执行未返回事件"));
            var completed =
                    events.stream()
                            .anyMatch(
                                    event ->
                                            event.type()
                                                    == ExecutionEventType.EXECUTION_COMPLETED);
            if (!completed) {
                throw new IllegalStateException("助理执行未完成: " + failureReason(events));
            }
            var response = finalReply(events);
            if (response.isBlank()) {
                throw new IllegalStateException("助理执行完成但未返回回复文本");
            }
            var durationMs = (System.nanoTime() - startedAt) / 1_000_000;

            saveCheckpoint(
                    execution.getId(),
                    "coordinator",
                    1,
                    "{\"result\":\"%s\",\"success\":true}"
                            .formatted(escapeJson(truncate(response, 2000))));

            completeExecution(execution.getId());
            emitEvent(
                    task.getId(),
                    execution.getId(),
                    null,
                    "task_completed",
                    "{\"success\":true,\"duration_ms\":%d}".formatted(durationMs));

            chatService.saveMessage(
                    task.getCreatorId(),
                    "AI",
                    task.getConversationId(),
                    "assistant",
                    "[任务完成: %s]\n%s".formatted(task.getTitle(), response));
            recordOutput(task, execution.getId(), response);
        } catch (Exception e) {
            failExecution(execution.getId(), e.getMessage());
            emitEvent(
                    task.getId(),
                    execution.getId(),
                    null,
                    "error",
                    "{\"message\":\"%s\"}".formatted(escapeJson(e.getMessage())));
            throw e;
        }
    }

    private AssistantCommand createCommand(
            ChatTask task, TaskExecution execution, String input) {
        if (task.getOrgId() == null) {
            throw new IllegalStateException("聊天任务缺少 tenant/org 归属: " + task.getId());
        }
        var tenantId = new TenantId(task.getOrgId().toString());
        var userId = new UserId(task.getCreatorId().toString());
        var conversationKey = "chat-conversation-" + task.getConversationId();
        var executionKey =
                "chat-task-%d-execution-%d".formatted(task.getId(), execution.getId());
        var now = Instant.now();
        return new AssistantCommand(
                AssistantCommand.Operation.START,
                tenantId,
                userId,
                new MemorySubject(tenantId, SubjectKind.USER, userId.value()),
                new AssistantId(DEFAULT_ASSISTANT_ID),
                DEFAULT_ASSISTANT_VERSION,
                new ConversationId(conversationKey),
                new SessionId(conversationKey),
                new TaskId(executionKey),
                new ExecutionId(executionKey),
                new RunId(executionKey),
                null,
                new CorrelationId("chat-task-" + task.getId()),
                null,
                new IdempotencyKey(executionKey),
                ControlMode.READ_ONLY,
                null,
                null,
                0,
                input,
                CompletionCriteria.responseDelivered(),
                List.of(),
                now);
    }

    private String finalReply(List<ExecutionEvent> events) {
        return events.reversed().stream()
                .filter(event -> event.type() == ExecutionEventType.MESSAGE_COMPLETED)
                .map(event -> event.payload().values().get("text"))
                .filter(Objects::nonNull)
                .map(Object::toString)
                .findFirst()
                .orElse("");
    }

    private String failureReason(List<ExecutionEvent> events) {
        return events.reversed().stream()
                .map(event -> event.payload().values().get("reason"))
                .filter(Objects::nonNull)
                .map(Object::toString)
                .findFirst()
                .orElse("未产生成功终态");
    }

    // === 检查点 ===

    @Transactional
    public TaskCheckpoint saveCheckpoint(
            Long executionId, String scope, int stepIndex, String stateJson) {
        checkpointStore.save(executionId, scope, stepIndex, stateJson);
        executionRepository
                .findById(executionId)
                .ifPresent(
                        exec -> {
                            // 检查点 ID 由 JpaCheckpointStore 管理，此处仅触发保存
                            executionRepository.save(exec);
                        });
        return null; // 返回值保持兼容，调用方不使用返回的对象
    }

    public TaskCheckpoint loadCheckpoint(Long executionId) {
        // 仅返回状态 JSON，调用方通过 loadCheckpointJson 使用
        return null;
    }

    public String loadCheckpointJson(Long executionId) {
        return checkpointStore.loadLatest(executionId).orElse(null);
    }

    // === 状态管理 ===

    @Transactional
    public void completeExecution(Long executionId) {
        executionRepository
                .findById(executionId)
                .ifPresent(
                        exec -> {
                            exec.setStatus(TaskExecutionStatus.DONE);
                            exec.setEndedAt(LocalDateTime.now());
                            executionRepository.save(exec);
                        });
    }

    @Transactional
    public void failExecution(Long executionId, String errorMessage) {
        executionRepository
                .findById(executionId)
                .ifPresent(
                        exec -> {
                            exec.setStatus(TaskExecutionStatus.FAILED);
                            exec.setEndedAt(LocalDateTime.now());
                            exec.setErrorMessage(errorMessage);
                            executionRepository.save(exec);
                        });
    }

    @Transactional
    public int recoverOrphans() {
        var cutoff = LocalDateTime.now().minusMinutes(ORPHAN_TIMEOUT_MINUTES);
        return executionRepository.recoverOrphans(cutoff);
    }

    // === 事件日志 ===

    @Transactional
    public void emitEvent(
            Long taskId, Long executionId, String subtaskKey, String type, String payload) {
        taskEventBus.publish(taskId, executionId, subtaskKey, type, payload);
    }

    public List<TaskEvent> getEvents(Long taskId) {
        var task = taskRepository.findById(taskId).orElseThrow();
        if (task.getOrgId() == null) {
            throw new IllegalStateException("聊天任务缺少 tenant/org 归属: " + taskId);
        }
        return eventStore
                .readTask(new TenantId(task.getOrgId().toString()), new TaskId(taskId.toString()), 0)
                .map(stored -> TaskEvent.from(stored.eventOffset(), stored.event()))
                .collectList()
                .blockOptional()
                .orElseGet(List::of);
    }

    // === 产出记录 ===

    private void recordOutput(ChatTask task, Long executionId, String result) {
        try {
            var output = new AiOutput();
            output.setSessionId(task.getConversationId());
            output.setTaskId(task.getId());
            output.setExecutionId(executionId);
            output.setCreatorId(task.getCreatorId());
            output.setSourceType(OutputSourceType.TASK);
            output.setCategory(detectCategory(result));
            output.setRiskLevel(detectRiskLevel(task, result));
            output.setTitle(task.getTitle());
            output.setDescription(truncate(result, 500));
            output.setContentSnapshot(
                    "{\"type\":\"task_result\",\"content\":\"%s\"}"
                            .formatted(escapeJson(truncate(result, 5000))));
            aiOutputService.record(output);
        } catch (Exception e) {
            log.warn("记录 AI 产出失败: taskId={}", task.getId(), e);
        }
    }

    private OutputCategory detectCategory(String result) {
        if (result == null) return OutputCategory.DOCUMENT;
        if (result.contains("```") || result.contains("class ") || result.contains("function "))
            return OutputCategory.CODE;
        if (result.contains("CREATE") || result.contains("UPDATE") || result.contains("DELETE"))
            return OutputCategory.ENTITY_CHANGE;
        return OutputCategory.DOCUMENT;
    }

    private RiskLevel detectRiskLevel(ChatTask task, String result) {
        if (result == null) return RiskLevel.LOW;
        if (result.contains("DELETE")
                || result.contains("删除")
                || result.contains("权限")
                || result.contains("DROP")
                || result.contains("TRUNCATE")) return RiskLevel.HIGH;
        if (result.contains("UPDATE") || result.contains("修改") || result.contains("CREATE"))
            return RiskLevel.MEDIUM;
        return RiskLevel.LOW;
    }

    // === 内部方法 ===

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

}
