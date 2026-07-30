package com.xuejiai.aaf.framework.task.queue;

/**
 * 队列任务处理器扩展点。
 *
 * <p>处理器必须按业务幂等键保证重复调用安全；框架幂等标记用于降低重复执行概率，不能替代业务事务约束。
 */
public interface TaskHandler {

    /** 全局唯一任务类型。 */
    String taskType();

    /** 执行业务载荷。失败时抛出运行时异常，由队列统一重试。 */
    void handle(String payload);

    /** 任务超时秒数，0 表示使用全局默认值。 */
    default long timeoutSeconds() {
        return 0;
    }
}
