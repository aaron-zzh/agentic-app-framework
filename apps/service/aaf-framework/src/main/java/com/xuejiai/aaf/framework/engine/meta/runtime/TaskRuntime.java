package com.xuejiai.aaf.framework.engine.meta.runtime;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.engine.workflow.WorkflowEngine;
import com.xuejiai.aaf.framework.task.TaskMonitor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 元引擎统一任务运行时。
 *
 * <p>三种触发器（定时/队列/请求）的共同执行归宿，提供统一的横切能力：
 *
 * <ul>
 *   <li>执行超时控制（默认 30 分钟，任务级可覆盖）
 *   <li>执行监控持久化（{@link TaskMonitor} 写 sys_task_execution）
 *   <li>重要任务完成/超时推送通知
 * </ul>
 *
 * <p><b>演进方向（v0.2+）：持久化长任务支持</b><br>
 * 当任务需要可中断/可恢复/子任务协调时（如 AI Chat 的 DurableTaskExecutor）， 当前由业务层自行实现状态机。未来在 {@code submit()} 内增加
 * {@code durable} 模式， 委托 {@code framework/engine/workflow/FlowableWorkflowEngine} 启动 Flowable 流程实例，
 * 由 Flowable 原生提供持久化状态机、检查点、子流程、人工节点等能力， 业务层的手写状态机（DurableTaskExecutor）随之退役。
 *
 * @author Kiro
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskRuntime {

    @Value("${aaf.task.runtime.timeout-seconds:1800}")
    private long timeoutSeconds;

    private final TaskMonitor taskMonitor;
    private final TaskNotifier taskNotifier;
    private final WorkflowEngine workflowEngine;

    private final Map<String, AafTask> registry = new ConcurrentHashMap<>();
    private final Map<String, Integer> progressMap = new ConcurrentHashMap<>();

    // ==================== 注册 ====================

    public void register(AafTask task) {
        registry.put(task.taskType(), task);
        log.debug("注册任务到元引擎运行时: {}", task.taskType());
    }

    public Optional<AafTask> find(String taskType) {
        return Optional.ofNullable(registry.get(taskType));
    }

    // ==================== 执行 ====================

    /**
     * 提交任务（无进度反馈）。定时触发和队列触发使用。
     *
     * @param taskType 任务类型
     * @param payload 输入 JSON（可为 null）
     * @param meta 触发元信息（triggerType/priority/bizId 等）
     */
    public TaskResult submit(String taskType, String payload, ExecutionMeta meta) {
        var task = registry.get(taskType);
        if (task == null) {
            log.warn("未找到任务: {}", taskType);
            return TaskResult.fail("未找到任务: " + taskType);
        }
        var executionId = newExecutionId();
        var monitorId =
                taskMonitor.recordStart(
                        taskType,
                        meta.triggerType(),
                        meta.bizId(),
                        meta.context(),
                        meta.priority(),
                        meta.triggerType());
        var ctx = new TaskContext(executionId, taskType, payload);
        try {
            // durable 任务：委托 Flowable 启动流程实例，由引擎原生提供持久化/重试/子任务能力
            if (task.durable()) {
                var processInstanceId =
                        workflowEngine.startProcess(taskType, executionId, ctx.variables());
                taskMonitor.recordSuccess(monitorId);
                log.info(
                        "持久化长任务已启动 Flowable 流程：type={}, processInstanceId={}",
                        taskType,
                        processInstanceId);
                return TaskResult.ok(processInstanceId);
            }
            var result = executeWithTimeout(task, ctx);
            recordResult(monitorId, result);
            notifyIfNeeded(ctx, result);
            return result;
        } catch (TimeoutException e) {
            log.warn("任务超时: {} ({}s)", taskType, timeoutSeconds);
            taskMonitor.recordFailure(monitorId, "任务执行超时");
            notifyTimeoutIfNeeded(ctx);
            return TaskResult.fail("任务执行超时");
        } catch (Exception e) {
            log.error("任务执行异常: {}", taskType, e);
            taskMonitor.recordFailure(monitorId, e.getMessage());
            return TaskResult.fail(e);
        }
    }

    /** 便捷重载——无元信息时使用 */
    public TaskResult submit(String taskType, String payload) {
        return submit(taskType, payload, ExecutionMeta.of("UNKNOWN"));
    }

    /** 提交任务（带进度反馈）。请求触发使用，返回 executionId 供前端轮询。 */
    public String submitWithProgress(
            String taskType, String payload, int total, Consumer<Integer> callback) {
        var task = registry.get(taskType);
        if (task == null) throw new IllegalArgumentException("未找到任务: " + taskType);
        var executionId = newExecutionId();
        progressMap.put(executionId, 0);
        var monitorId = taskMonitor.recordStart(taskType, "REQUEST", null, null, null, "REQUEST");
        var ctx =
                new TaskContext(
                        executionId,
                        taskType,
                        payload,
                        progress -> {
                            progressMap.put(executionId, progress);
                            if (callback != null) callback.accept(progress);
                        });
        try {
            var result = executeWithTimeout(task, ctx);
            progressMap.put(executionId, 100);
            recordResult(monitorId, result);
            notifyIfNeeded(ctx, result);
        } catch (TimeoutException e) {
            log.warn("任务超时: {} ({}s)", taskType, timeoutSeconds);
            taskMonitor.recordFailure(monitorId, "任务执行超时");
            notifyTimeoutIfNeeded(ctx);
        } catch (Exception e) {
            log.error("任务执行异常: {}", taskType, e);
            taskMonitor.recordFailure(monitorId, e.getMessage());
        } finally {
            progressMap.remove(executionId);
        }
        return executionId;
    }

    /** 查询进度（0-100），-1 表示不存在或已完成 */
    public int getProgress(String executionId) {
        return progressMap.getOrDefault(executionId, -1);
    }

    // ==================== 内部方法 ====================

    private TaskResult executeWithTimeout(AafTask task, TaskContext ctx) throws Exception {
        var timeout = task.timeoutSeconds() > 0 ? task.timeoutSeconds() : timeoutSeconds;
        var future = CompletableFuture.supplyAsync(() -> task.execute(ctx));
        return future.orTimeout(timeout, TimeUnit.SECONDS)
                .exceptionally(
                        e -> {
                            if (e.getCause() instanceof TimeoutException) {
                                throw new RuntimeException(new TimeoutException());
                            }
                            throw new RuntimeException(e.getCause());
                        })
                .join();
    }

    private void recordResult(Long monitorId, TaskResult result) {
        if (result.success()) taskMonitor.recordSuccess(monitorId);
        else taskMonitor.recordFailure(monitorId, result.error());
    }

    private void notifyIfNeeded(TaskContext ctx, TaskResult result) {
        var userId = ctx.<Long>getVariable("notifyUserId");
        var important = Boolean.TRUE.equals(ctx.<Boolean>getVariable("important"));
        if (userId != null && important) {
            taskNotifier.notifyComplete(userId, ctx.taskType(), result);
        }
    }

    private void notifyTimeoutIfNeeded(TaskContext ctx) {
        var userId = ctx.<Long>getVariable("notifyUserId");
        var important = Boolean.TRUE.equals(ctx.<Boolean>getVariable("important"));
        if (userId != null && important) {
            taskNotifier.notifyTimeout(userId, ctx.taskType());
        }
    }

    private String newExecutionId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
