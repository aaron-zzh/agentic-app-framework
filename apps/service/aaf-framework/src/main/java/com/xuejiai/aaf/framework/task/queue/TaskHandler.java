package com.xuejiai.aaf.framework.task.queue;

/** 任务处理器接口。业务方实现此接口并注册为 Spring Bean。 */
public interface TaskHandler {

    /** 处理的任务类型 */
    String taskType();

    /** 处理任务 */
    void handle(AsyncTaskMessage task);
}
