package com.xuejiai.aaf.framework.task;

/**
 * 定时任务定义。
 *
 * @param name 任务名称（唯一标识）
 * @param cronExpression Cron 表达式（triggerType=CRON 时使用）
 * @param intervalMs 间隔毫秒数（triggerType=FIXED_DELAY/FIXED_RATE 时使用）
 * @param triggerType 触发类型：CRON / FIXED_DELAY / FIXED_RATE
 * @param taskClass 任务执行类（实现 Runnable）
 * @param enabled 是否启用
 * @param description 任务描述
 * @param misfirePolicy 错过执行补偿策略（仅 CRON 有效）
 */
public record TaskDefinition(
        String name,
        String cronExpression,
        Long intervalMs,
        TriggerType triggerType,
        Class<? extends Runnable> taskClass,
        boolean enabled,
        String description,
        MisfirePolicy misfirePolicy) {

    /** Cron 任务快捷构造 */
    public TaskDefinition(String name, String cronExpression, Class<? extends Runnable> taskClass) {
        this(
                name,
                cronExpression,
                null,
                TriggerType.CRON,
                taskClass,
                true,
                "",
                MisfirePolicy.IGNORE);
    }

    /** Cron 任务完整构造（兼容旧代码） */
    public TaskDefinition(
            String name,
            String cronExpression,
            Class<? extends Runnable> taskClass,
            boolean enabled,
            String description) {
        this(
                name,
                cronExpression,
                null,
                TriggerType.CRON,
                taskClass,
                enabled,
                description,
                MisfirePolicy.IGNORE);
    }

    /** Cron 任务含 misfire 构造（兼容旧代码） */
    public TaskDefinition(
            String name,
            String cronExpression,
            Class<? extends Runnable> taskClass,
            boolean enabled,
            String description,
            MisfirePolicy misfirePolicy) {
        this(
                name,
                cronExpression,
                null,
                TriggerType.CRON,
                taskClass,
                enabled,
                description,
                misfirePolicy);
    }

    /** FIXED_DELAY / FIXED_RATE 任务快捷构造 */
    public static TaskDefinition fixedDelay(
            String name, long intervalMs, Class<? extends Runnable> taskClass) {
        return new TaskDefinition(
                name,
                null,
                intervalMs,
                TriggerType.FIXED_DELAY,
                taskClass,
                true,
                "",
                MisfirePolicy.IGNORE);
    }

    public static TaskDefinition fixedRate(
            String name, long intervalMs, Class<? extends Runnable> taskClass) {
        return new TaskDefinition(
                name,
                null,
                intervalMs,
                TriggerType.FIXED_RATE,
                taskClass,
                true,
                "",
                MisfirePolicy.IGNORE);
    }
}
