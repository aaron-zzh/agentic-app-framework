package com.xuejiai.aaf.module.system.task.async;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/** 异步任务服务。提交任务 → 后台执行 → 进度可查询。 */
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
