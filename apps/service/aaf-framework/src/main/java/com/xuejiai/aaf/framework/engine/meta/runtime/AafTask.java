package com.xuejiai.aaf.framework.engine.meta.runtime;

/**
 * AAF 统一任务接口。
 *
 * <p>所有可执行任务的顶层契约，三种触发方式均实现此接口：
 *
 * <ul>
 *   <li>定时任务（{@code framework/task/}）：cron/fixedDelay/fixedRate 触发
 *   <li>队列任务（{@code framework/task/queue/}）：Redis Stream 消息触发
 *   <li>请求任务（{@code aaf-api/.../async/}）：HTTP 请求触发，含进度反馈
 * </ul>
 *
 * <p>原包中的具体实现（{@code Runnable}、{@code TaskHandler}）保持不变， 通过适配器实现此接口接入统一运行时。
 *
 * @author AaronZZH
 */
public interface AafTask {

    /** 任务类型标识，全局唯一。 定时任务对应 {@code TaskDefinition.name()}，队列任务对应 {@code AsyncTaskMessage.type()}。 */
    String taskType();

    /**
     * 任务超时秒数。0 表示使用 {@code aaf.task.runtime.timeout-seconds} 全局默认值。 定时任务通过 {@link
     * com.xuejiai.aaf.framework.task.ScheduledTaskAdapter} 从 DB 读取。
     */
    default long timeoutSeconds() {
        return 0;
    }

    /**
     * 是否为持久化长任务。
     *
     * <p>返回 true 时，{@link TaskRuntime} 委托 {@code FlowableWorkflowEngine} 启动流程实例， 由 Flowable
     * 原生提供状态持久化、检查点、子流程、人工节点等能力。 对应的 BPMN 流程定义 key 与 {@link #taskType()} 相同。 默认 false，兼容现有任务不受影响。
     */
    default boolean durable() {
        return false;
    }

    /**
     * 执行任务。
     *
     * @param context 执行上下文，含输入 payload、进度回调、流程变量
     * @return 执行结果
     */
    TaskResult execute(TaskContext context);
}
