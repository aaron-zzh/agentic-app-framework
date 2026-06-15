package com.xuejiai.aaf.module.system.task.async;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/**
 * 内存异步任务服务。提交任务 → @Async 线程池后台执行 → 进度可查询。
 *
 * <h3>适用场景</h3>
 *
 * <ul>
 *   <li>用户主动触发（HTTP 请求）的批量操作，需要前端进度条反馈
 *   <li>操作耗时在分钟级以内，重启丢失进度可接受
 *   <li>典型：批量删除用户、批量导入/导出
 * </ul>
 *
 * <h3>不适用场景</h3>
 *
 * <ul>
 *   <li>需要持久化、重启恢复 → 用 Redis Stream 任务队列
 *   <li>需要定时调度 → 用 ScheduledTaskService
 *   <li>需要人工审批、多步骤状态流转 → 用 Flowable 工作流
 * </ul>
 *
 * @author AaronZZH & Kiro
 */
@Service
@RequiredArgsConstructor
public class AsyncTaskService {

    private final AsyncTaskExecutor executor;
    private final Map<String, AsyncTask> tasks = new ConcurrentHashMap<>();

    /** 提交异步任务，立即返回 taskId。 */
    public String submit(String type, int total, Consumer<AsyncTask> taskLogic) {
        var task = new AsyncTask();
        task.setTaskId(UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        task.setType(type);
        task.setTotal(total);
        task.setCreateTime(LocalDateTime.now());
        tasks.put(task.getTaskId(), task);
        executor.execute(task, taskLogic);
        return task.getTaskId();
    }

    /** 查询任务进度。 */
    public AsyncTask getProgress(String taskId) {
        return tasks.get(taskId);
    }
}
