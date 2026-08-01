package com.xuejiai.aaf.framework.task.queue;

/**
 * 队列任务处理器扩展点。
 *
 * <p>关系库副作用由框架事务性 inbox 与消费完成记录原子提交；外部系统副作用无法参与本地事务，处理器必须使用稳定任务 ID 作为供应商幂等键。
 */
public interface TaskHandler {

    /** 全局唯一任务类型。 */
    String taskType();

    /** 执行业务载荷。taskId 跨重试保持稳定；失败时抛出运行时异常，由队列统一重试。 */
    void handle(String taskId, String payload);

    /** 任务超时秒数，0 表示使用全局默认值。 */
    default long timeoutSeconds() {
        return 0;
    }
}
