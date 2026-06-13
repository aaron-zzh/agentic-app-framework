package com.xuejiai.aaf.framework.task;

/** 错过执行补偿策略。 */
public enum MisfirePolicy {
    /** 忽略——不补跑，下次正常触发（默认，适合高频轮询任务如 ImageSyncJob） */
    IGNORE,
    /** 补跑一次——只补最近一次，适合低频重要任务如积分发放、订阅结算 */
    RUN_ONCE
}
