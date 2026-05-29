package com.xuejiai.aaf.module.ai.chat.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xuejiai.aaf.framework.intelligent.assistant.AssistantService;
import com.xuejiai.aaf.module.ai.chat.domain.ChatTask;
import com.xuejiai.aaf.module.ai.chat.domain.TaskCheckpoint;
import com.xuejiai.aaf.module.ai.chat.domain.TaskEvent;
import com.xuejiai.aaf.module.ai.chat.domain.TaskExecution;
import com.xuejiai.aaf.module.ai.chat.repository.TaskCheckpointRepository;
import com.xuejiai.aaf.module.ai.chat.repository.TaskEventRepository;
import com.xuejiai.aaf.module.ai.chat.repository.TaskExecutionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 持久任务执行器——保障长任务可恢复、可观测、状态一致。
 *
 * <p>核心能力：
 * <ul>
 *   <li>创建执行实例（支持重试 attempt_no 递增）
 *   <li>CAS 抢占启动（多实例安全）
 *   <li>检查点保存/恢复
 *   <li>事件日志记录
 *   <li>多子任务 fork/join 协调
 *   <li>孤儿回收
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DurableTaskExecutor {

    private final TaskExecutionRepository executionRepository;
    private final TaskCheckpointRepository checkpointRepository;
    private final TaskEventRepository eventRepository;
    private final AssistantService assistantService;
    private final ChatService chatService;
    private final ObjectMapper objectMapper;

    private static final int ORPHAN_TIMEOUT_MINUTES = 10;

    /** 创建主执行实例 */
    @Transactional
    public TaskExecution createExecution(ChatTask task) {
        var existing = executionRepository.findFirstByTaskIdOrderByAttemptNoDesc(task.getId());
        int attemptNo = existing.map(e -> e.getAttemptNo() + 1).orElse(1);

        var execution = new TaskExecution();
        execution.setTaskId(task.getId());
        execution.setAttemptNo(attemptNo);
        execution.setStatus("pending");
        executionRepository.save(execution);

        emitEvent(task.getId(), execution.getId(), null, "execution_created",
                "{\"attemptNo\":%d}".formatted(attemptNo));
        return execution;
    }

    /** 创建子执行实例 */
    @Transactional
    public TaskExecution createSubExecution(Long taskId, Long parentExecutionId, String subtaskKey, String role) {
        var sub = new TaskExecution();
        sub.setTaskId(taskId);
        sub.setParentExecutionId(parentExecutionId);
        sub.setSubtaskKey(subtaskKey);
        sub.setRole(role);
        sub.setAttemptNo(1);
        sub.setStatus("pending");
        executionRepository.save(sub);

        emitEvent(taskId, parentExecutionId, subtaskKey, "subtask_forked",
                "{\"role\":\"%s\",\"executionId\":%d}".formatted(role, sub.getId()));
        return sub;
    }

    /** CAS 抢占启动执行 */
    @Transactional
    public boolean tryStart(Long executionId) {
        return executionRepository.casStart(executionId) > 0;
    }

    /** 执行单个任务（主执行，无子任务拆分） */
    public void execute(ChatTask task, TaskExecution execution) {
        emitEvent(task.getId(), execution.getId(), null, "task_started",
                "{\"title\":\"%s\"}".formatted(escapeJson(task.getTitle())));

        try {
            var input = task.getDescription() != null
                    ? task.getTitle() + "\n" + task.getDescription()
                    : task.getTitle();

            var response = assistantService.handle(
                    task.getSessionId().toString(),
                    task.getCreatorId(),
                    "default",
                    input);

            // 保存检查点
            saveCheckpoint(execution.getId(), "coordinator", 1,
                    "{\"result\":\"%s\"}".formatted(escapeJson(truncate(response.content(), 2000))));

            // 完成
            completeExecution(execution.getId(), null);
            emitEvent(task.getId(), execution.getId(), null, "task_completed",
                    "{\"success\":true}");

            // 持久化回复
            chatService.saveMessage(
                    task.getCreatorId(), "AI", task.getSessionId(),
                    "assistant", "[任务完成: %s]\n%s".formatted(task.getTitle(), response.content()));

        } catch (Exception e) {
            failExecution(execution.getId(), e.getMessage());
            emitEvent(task.getId(), execution.getId(), null, "error",
                    "{\"message\":\"%s\",\"recoverable\":true}".formatted(escapeJson(e.getMessage())));
            throw e;
        }
    }

    /** 执行子任务 */
    public String executeSubtask(ChatTask task, TaskExecution subExecution, String input) {
        emitEvent(task.getId(), subExecution.getId(), subExecution.getSubtaskKey(),
                "step_started", "{\"input\":\"%s\"}".formatted(escapeJson(truncate(input, 200))));

        var response = assistantService.handle(
                task.getSessionId().toString(),
                task.getCreatorId(),
                "default",
                input);

        saveCheckpoint(subExecution.getId(), "subtask", 1,
                "{\"result\":\"%s\"}".formatted(escapeJson(truncate(response.content(), 2000))));

        completeExecution(subExecution.getId(), null);
        emitEvent(task.getId(), subExecution.getId(), subExecution.getSubtaskKey(),
                "subtask_completed", "{\"success\":true}");

        return response.content();
    }

    // === 检查点 ===

    /** 保存检查点 */
    @Transactional
    public TaskCheckpoint saveCheckpoint(Long executionId, String scope, int stepIndex, String stateJson) {
        var cp = new TaskCheckpoint();
        cp.setExecutionId(executionId);
        cp.setScope(scope);
        cp.setStepIndex(stepIndex);
        cp.setStateJson(stateJson);
        checkpointRepository.save(cp);

        // 更新执行实例的 checkpoint_id
        executionRepository.findById(executionId).ifPresent(exec -> {
            exec.setCheckpointId(cp.getId());
            executionRepository.save(exec);
        });

        return cp;
    }

    /** 加载最新检查点 */
    public TaskCheckpoint loadCheckpoint(Long executionId) {
        return checkpointRepository.findFirstByExecutionIdOrderByStepIndexDesc(executionId).orElse(null);
    }

    // === 状态管理 ===

    @Transactional
    public void completeExecution(Long executionId, String errorMessage) {
        executionRepository.findById(executionId).ifPresent(exec -> {
            exec.setStatus("done");
            exec.setEndedAt(LocalDateTime.now());
            executionRepository.save(exec);
        });
    }

    @Transactional
    public void failExecution(Long executionId, String errorMessage) {
        executionRepository.findById(executionId).ifPresent(exec -> {
            exec.setStatus("failed");
            exec.setEndedAt(LocalDateTime.now());
            exec.setErrorMessage(errorMessage);
            executionRepository.save(exec);
        });
    }

    /** 孤儿回收 */
    @Transactional
    public int recoverOrphans() {
        var cutoff = LocalDateTime.now().minusMinutes(ORPHAN_TIMEOUT_MINUTES);
        return executionRepository.recoverOrphans(cutoff);
    }

    /** 获取子执行列表 */
    public List<TaskExecution> getSubExecutions(Long parentExecutionId) {
        return executionRepository.findByParentExecutionIdOrderBySubtaskKey(parentExecutionId);
    }

    // === 事件日志 ===

    @Transactional
    public void emitEvent(Long taskId, Long executionId, String subtaskKey, String type, String payload) {
        var event = TaskEvent.of(taskId, executionId, subtaskKey, type, payload);
        eventRepository.save(event);
    }

    /** 获取任务事件日志 */
    public List<TaskEvent> getEvents(Long taskId) {
        return eventRepository.findByTaskIdOrderByCreateTimeAsc(taskId);
    }

    // === 工具方法 ===

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
