package com.xuejiai.aaf.framework.task;

/**
 * 定时任务定义。
 *
 * @param name 任务名称（唯一标识）
 * @param cronExpression Cron 表达式
 * @param taskClass 任务执行类（实现 Runnable）
 * @param enabled 是否启用
 * @param description 任务描述
 */
public record TaskDefinition(
        String name, String cronExpression, Class<? extends Runnable> taskClass, boolean enabled, String description) {

    public TaskDefinition(String name, String cronExpression, Class<? extends Runnable> taskClass) {
        this(name, cronExpression, taskClass, true, "");
    }
}
