package com.xuejiai.aaf.module.system.task.async;

import java.util.function.Consumer;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 异步任务执行器。独立 Bean 确保 @Async 代理生效。
 *
 * @author AaronZZH & Kiro
 */
@Component
public class AsyncTaskExecutor {

    @Async
    public void execute(AsyncTask task, Consumer<AsyncTask> executor) {
        task.setStatus(AsyncTask.Status.RUNNING);
        try {
            executor.accept(task);
            task.setStatus(AsyncTask.Status.COMPLETED);
            task.setCompleteTime(java.time.LocalDateTime.now());
        } catch (Exception e) {
            task.setStatus(AsyncTask.Status.FAILED);
            task.setErrorMessage(e.getMessage());
            task.setCompleteTime(java.time.LocalDateTime.now());
        }
    }
}
