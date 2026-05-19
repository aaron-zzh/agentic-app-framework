package com.xuejiai.aaf.framework.task.queue;

import java.time.Duration;

/** 任务队列接口。 */
public interface TaskQueue {

    /** 入队，返回消息 ID */
    String enqueue(AsyncTaskMessage task);

    /** 延迟入队 */
    void enqueueWithDelay(AsyncTaskMessage task, Duration delay);
}
