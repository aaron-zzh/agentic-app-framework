package com.xuejiai.aaf.framework.task.queue;

import java.util.List;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.engine.meta.runtime.AafTask;
import com.xuejiai.aaf.framework.engine.meta.runtime.TaskContext;
import com.xuejiai.aaf.framework.engine.meta.runtime.TaskResult;
import com.xuejiai.aaf.framework.engine.meta.runtime.TaskRuntime;

import lombok.RequiredArgsConstructor;

/** 自动注册所有队列任务处理器。 */
@Component
@RequiredArgsConstructor
public class TaskHandlerRegistrar implements InitializingBean {

    private final List<TaskHandler> handlers;
    private final TaskRuntime taskRuntime;
    private final TaskInboxExecutor taskInboxExecutor;

    @Override
    public void afterPropertiesSet() {
        handlers.forEach(
                handler ->
                        taskRuntime.register(new HandlerTaskAdapter(handler, taskInboxExecutor)));
    }

    private record HandlerTaskAdapter(TaskHandler handler, TaskInboxExecutor taskInboxExecutor)
            implements AafTask {

        @Override
        public String taskType() {
            return handler.taskType();
        }

        @Override
        public long timeoutSeconds() {
            return handler.timeoutSeconds();
        }

        @Override
        public TaskResult execute(TaskContext context) {
            var queueTaskId = context.<String>getVariable(TaskContext.QUEUE_TASK_ID);
            if (queueTaskId == null || queueTaskId.isBlank()) {
                throw new IllegalStateException("队列任务缺少稳定 taskId: " + handler.taskType());
            }
            taskInboxExecutor.execute(
                    queueTaskId,
                    handler.taskType(),
                    context.payload(),
                    () -> handler.handle(queueTaskId, context.payload()));
            return TaskResult.ok();
        }
    }
}
