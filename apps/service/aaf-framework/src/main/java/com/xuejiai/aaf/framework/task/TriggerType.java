package com.xuejiai.aaf.framework.task;

/** 任务触发类型。 */
public enum TriggerType {
    /** Cron 表达式触发（默认） */
    CRON,
    /** 固定间隔触发——上次执行结束后等待指定毫秒数再次执行（对应 Spring fixedDelay） */
    FIXED_DELAY,
    /** 固定频率触发——按固定周期执行，不等待上次结束（对应 Spring fixedRate） */
    FIXED_RATE
}
