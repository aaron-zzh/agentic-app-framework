package com.xuejiai.aaf.framework.task.queue;

/**
 * 队列任务处理器扩展点。
 *
 * <p>inbox 仅持久化 claim、完成和释放状态；handler 在事务外运行。处理器的关系库副作用需自行保持幂等，外部系统调用必须使用稳定任务 ID 或持久检查点避免重试重复执行。
 */
public interface TaskHandler {

    /** 全局唯一任务类型。 */
    String taskType();

    /** 执行业务载荷并返回结果摘要。taskId 跨重试保持稳定；失败时抛出运行时异常，由队列统一重试。 */
    String handle(String taskId, String payload);

    /** 任务超时秒数，0 表示使用全局默认值。 */
    default long timeoutSeconds() {
        return 0;
    }
}
