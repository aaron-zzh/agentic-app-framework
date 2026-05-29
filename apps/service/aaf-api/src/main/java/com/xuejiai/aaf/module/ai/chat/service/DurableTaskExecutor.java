package com.xuejiai.aaf.module.ai.chat.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.framework.intelligent.agent.AgentDefinition;
import com.xuejiai.aaf.framework.intelligent.agent.AgentRegistryService;
import com.xuejiai.aaf.framework.intelligent.agent.CognitiveCycleExecutor;
import com.xuejiai.aaf.framework.intelligent.assistant.TaskBoard;
import com.xuejiai.aaf.module.ai.chat.domain.ChatTask;
import com.xuejiai.aaf.module.ai.chat.domain.TaskCheckpoint;
import com.xuejiai.aaf.module.ai.chat.domain.TaskEvent;
import com.xuejiai.aaf.module.ai.chat.domain.TaskExecution;
import com.xuejiai.aaf.module.ai.chat.repository.TaskCheckpointRepository;
import com.xuejiai.aaf.module.ai.chat.repository.TaskEventRepository;
import com.xuejiai.aaf.module.ai.chat.repository.TaskExecutionRepository;
import com.xuejiai.aaf.module.ai.output.domain.AiOutput;
import com.xuejiai.aaf.module.ai.output.service.AiOutputService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 持久任务执行器——委托 framework 层组件实现可恢复、可观测、状态一致的长任务执行。
 *
 * <p>职责分工：
 * <ul>
 *   <li>本类：入口 + DB 持久化（执行实例/检查点/事件日志）+ 调度协调
 *   <li>TaskBoard（framework）：子任务管理 + 依赖检查 + 快照/恢复
 *   <li>CognitiveCycleExecutor（framework）：Agent 认知循环 + 步骤级检查点
 *   <li>CheckpointStore（framework/engine）：通用检查点持久化
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DurableTaskExecutor {

    private final TaskExecutionRepository executionRepository;
    private final TaskCheckpointRepository checkpointRepository;
    private final TaskEventRepository eventRepository;
    private final TaskEventStreamService eventStreamService;
    private final CognitiveCycleExecutor cognitiveCycleExecutor;
    private final AgentRegistryService agentRegistry;
    private final ChatService chatService;
    private final AiOutputService aiOutputService;

    private static final int ORPHAN_TIMEOUT_MINUTES = 10;

    // === 执行实例管理 ===

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

    /** 创建子执行实例（fork） */
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

    /** CAS 抢占启动 */
    @Transactional
    public boolean tryStart(Long executionId) {
        return executionRepository.casStart(executionId) > 0;
    }

    // === 单任务执行（委托 CognitiveCycleExecutor） ===

    /** 执行单个任务——委托 Agent 认知循环 */
    public void execute(ChatTask task, TaskExecution execution) {
        emitEvent(task.getId(), execution.getId(), null, "task_started",
                "{\"title\":\"%s\"}".formatted(escapeJson(task.getTitle())));

        try {
            var input = task.getDescription() != null
                    ? task.getTitle() + "\n" + task.getDescription()
                    : task.getTitle();

            // 委托 CognitiveCycleExecutor（含记忆检索、检查点、重试）
            var agentDef = resolveAgent(task);
            var result = cognitiveCycleExecutor.execute(
                    agentDef, input, task.getCreatorId(),
                    task.getSessionId().toString(), null, null);

            // 保存协调者检查点
            saveCheckpoint(execution.getId(), "coordinator", 1,
                    "{\"result\":\"%s\",\"success\":%b}".formatted(
                            escapeJson(truncate(result.response(), 2000)), result.success()));

            completeExecution(execution.getId());
            emitEvent(task.getId(), execution.getId(), null, "task_completed",
                    "{\"success\":%b,\"duration_ms\":%d}".formatted(result.success(), result.duration().toMillis()));

            // 持久化回复到对话
            chatService.saveMessage(
                    task.getCreatorId(), "AI", task.getSessionId(),
                    "assistant", "[任务完成: %s]\n%s".formatted(task.getTitle(), result.response()));

            // 记录 AI 产出
            recordOutput(task, execution.getId(), result.response());

        } catch (Exception e) {
            failExecution(execution.getId(), e.getMessage());
            emitEvent(task.getId(), execution.getId(), null, "error",
                    "{\"message\":\"%s\"}".formatted(escapeJson(e.getMessage())));
            throw e;
        }
    }

    // === 多子任务执行（委托 TaskBoard + fork） ===

    /** 执行多子任务——使用 TaskBoard 管理依赖和并发 */
    public void executeWithSubtasks(ChatTask task, TaskExecution execution, TaskBoard taskBoard) {
        emitEvent(task.getId(), execution.getId(), null, "task_started",
                "{\"subtaskCount\":%d}".formatted(taskBoard.allTasks().size()));

        // 保存初始 TaskBoard 检查点
        saveCheckpoint(execution.getId(), "coordinator", 0,
                taskBoard.toSnapshot().toString());

        // 循环执行直到所有子任务完成
        while (!taskBoard.isAllFinished()) {
            var next = taskBoard.nextReady();
            if (next.isEmpty()) {
                // 所有可执行的都在 running 或有未满足依赖，等待
                sleep(1000);
                continue;
            }

            var subtask = next.get();
            taskBoard.markRunning(subtask.id());

            // fork 子执行
            var subExec = createSubExecution(task.getId(), execution.getId(), subtask.id(), subtask.id());
            if (!tryStart(subExec.getId())) continue;

            emitEvent(task.getId(), execution.getId(), subtask.id(), "step_started",
                    "{\"description\":\"%s\"}".formatted(escapeJson(subtask.description())));

            try {
                var agentDef = resolveAgent(task);
                var result = cognitiveCycleExecutor.execute(
                        agentDef, subtask.description(), task.getCreatorId(),
                        task.getSessionId().toString(), null, null);

                taskBoard.markDone(subtask.id(), result.response());
                completeExecution(subExec.getId());
                emitEvent(task.getId(), execution.getId(), subtask.id(), "subtask_completed",
                        "{\"success\":true}");
            } catch (Exception e) {
                taskBoard.markFailed(subtask.id(), e.getMessage());
                failExecution(subExec.getId(), e.getMessage());
                emitEvent(task.getId(), execution.getId(), subtask.id(), "error",
                        "{\"message\":\"%s\"}".formatted(escapeJson(e.getMessage())));
            }

            // 每完成一个子任务保存检查点
            saveCheckpoint(execution.getId(), "coordinator",
                    (int) taskBoard.allTasks().stream().filter(t -> t.status() == TaskBoard.TaskStatus.DONE).count(),
                    taskBoard.toSnapshot().toString());
        }

        // 聚合结果
        if (taskBoard.hasFailure()) {
            failExecution(execution.getId(), "部分子任务失败");
        } else {
            completeExecution(execution.getId());
        }
        emitEvent(task.getId(), execution.getId(), null,
                taskBoard.hasFailure() ? "task_failed" : "task_completed",
                "{\"done\":%d,\"failed\":%d}".formatted(
                        taskBoard.allTasks().stream().filter(t -> t.status() == TaskBoard.TaskStatus.DONE).count(),
                        taskBoard.allTasks().stream().filter(t -> t.status() == TaskBoard.TaskStatus.FAILED).count()));
    }

    // === 检查点 ===

    @Transactional
    public TaskCheckpoint saveCheckpoint(Long executionId, String scope, int stepIndex, String stateJson) {
        var cp = new TaskCheckpoint();
        cp.setExecutionId(executionId);
        cp.setScope(scope);
        cp.setStepIndex(stepIndex);
        cp.setStateJson(stateJson);
        checkpointRepository.save(cp);

        executionRepository.findById(executionId).ifPresent(exec -> {
            exec.setCheckpointId(cp.getId());
            executionRepository.save(exec);
        });
        return cp;
    }

    public TaskCheckpoint loadCheckpoint(Long executionId) {
        return checkpointRepository.findFirstByExecutionIdOrderByStepIndexDesc(executionId).orElse(null);
    }

    // === 状态管理 ===

    @Transactional
    public void completeExecution(Long executionId) {
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

    @Transactional
    public int recoverOrphans() {
        var cutoff = LocalDateTime.now().minusMinutes(ORPHAN_TIMEOUT_MINUTES);
        return executionRepository.recoverOrphans(cutoff);
    }

    // === 事件日志 ===

    @Transactional
    public void emitEvent(Long taskId, Long executionId, String subtaskKey, String type, String payload) {
        var event = TaskEvent.of(taskId, executionId, subtaskKey, type, payload);
        eventRepository.save(event);
        eventStreamService.broadcast(event);
    }

    public List<TaskEvent> getEvents(Long taskId) {
        return eventRepository.findByTaskIdOrderByCreateTimeAsc(taskId);
    }

    // === 产出记录 ===

    private void recordOutput(ChatTask task, Long executionId, String result) {
        try {
            var output = new AiOutput();
            output.setSessionId(task.getSessionId());
            output.setTaskId(task.getId());
            output.setExecutionId(executionId);
            output.setCreatorId(task.getCreatorId());
            output.setSourceType("task");
            output.setCategory(detectCategory(result));
            output.setRiskLevel(detectRiskLevel(task, result));
            output.setTitle(task.getTitle());
            output.setDescription(truncate(result, 500));
            output.setContentSnapshot("{\"type\":\"task_result\",\"content\":\"%s\"}".formatted(escapeJson(truncate(result, 5000))));
            aiOutputService.record(output);
        } catch (Exception e) {
            log.warn("记录 AI 产出失败: taskId={}", task.getId(), e);
        }
    }

    private String detectCategory(String result) {
        if (result == null) return "document";
        if (result.contains("```") || result.contains("class ") || result.contains("function ")) return "code";
        if (result.contains("CREATE") || result.contains("UPDATE") || result.contains("DELETE")) return "entity_change";
        return "document";
    }

    private String detectRiskLevel(ChatTask task, String result) {
        if (result == null) return "low";
        // 高风险关键词
        if (result.contains("DELETE") || result.contains("删除") || result.contains("权限")
                || result.contains("DROP") || result.contains("TRUNCATE")) return "high";
        // 中风险
        if (result.contains("UPDATE") || result.contains("修改") || result.contains("CREATE")) return "medium";
        return "low";
    }

    // === 内部方法 ===

    private AgentDefinition resolveAgent(ChatTask task) {
        // 优先使用任务关联的 Agent，否则用默认
        var def = agentRegistry.findById("default");
        if (def == null) {
            def = new AgentDefinition();
            def.setAgentId("default");
            def.setName("默认助理");
            def.setSystemPrompt("你是一个有帮助的 AI 助手，请完成用户交给你的任务。");
            def.setTimeoutSeconds(120);
        }
        return def;
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
