package com.xuejiai.aaf.framework.task;

/**
 * 定时任务执行上下文——持有当前任务的数据归属者 ID。
 *
 * <p>用于在定时任务线程中传递用户身份，使 {@link com.xuejiai.aaf.framework.engine.credit.AiCreditAspect} 等依赖 {@link
 * com.xuejiai.aaf.framework.security.OperatorContext} 的切面能正确取到用户 ID。
 *
 * <p>生命周期由 {@link ScheduledTaskExecutor} 管理：执行前 {@link #set}，执行后 {@link #clear}。
 *
 * <p>{@code ownerId = null} 表示系统任务，不属于任何用户。
 */
public final class TaskExecutionContextHolder {

    private static final ThreadLocal<Long> OWNER_ID = new ThreadLocal<>();

    private TaskExecutionContextHolder() {}

    public static void set(Long ownerId) {
        OWNER_ID.set(ownerId);
    }

    public static Long get() {
        return OWNER_ID.get();
    }

    public static void clear() {
        OWNER_ID.remove();
    }
}
