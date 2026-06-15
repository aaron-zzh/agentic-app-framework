package com.xuejiai.aaf.framework.task;

import com.xuejiai.aaf.framework.engine.meta.runtime.AafTask;
import com.xuejiai.aaf.framework.engine.meta.runtime.TaskContext;
import com.xuejiai.aaf.framework.engine.meta.runtime.TaskResult;

/**
 * 定时任务适配器：将 {@link Runnable} Bean 包装为 {@link AafTask}，接入统一运行时。
 *
 * <p>由 {@link DbTaskPersistencePort} 在加载 {@link TaskDefinition} 时自动创建， 注册到 {@link
 * com.xuejiai.aaf.framework.engine.meta.runtime.TaskRuntime}。 定时触发链路不变（{@link
 * ScheduledTaskExecutor} 仍负责调度）， 执行时通过 {@link
 * com.xuejiai.aaf.framework.engine.meta.runtime.TaskRuntime#submit} 统一分发。
 *
 * @author AaronZZH
 */
public class ScheduledTaskAdapter implements AafTask {

    private final String name;
    private final Runnable delegate;
    private final long timeoutSeconds;

    public ScheduledTaskAdapter(String name, Runnable delegate, long timeoutSeconds) {
        this.name = name;
        this.delegate = delegate;
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public String taskType() {
        return name;
    }

    @Override
    public long timeoutSeconds() {
        return timeoutSeconds;
    }

    @Override
    public TaskResult execute(TaskContext context) {
        try {
            delegate.run();
            return TaskResult.ok();
        } catch (Exception e) {
            return TaskResult.fail(e);
        }
    }
}
