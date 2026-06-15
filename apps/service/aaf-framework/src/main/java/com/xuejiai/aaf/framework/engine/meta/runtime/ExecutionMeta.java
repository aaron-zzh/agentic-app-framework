package com.xuejiai.aaf.framework.engine.meta.runtime;

/**
 * 任务执行元信息——触发器层传入，用于执行监控记录。 与 {@link TaskContext} 分离：TaskContext 是业务上下文，ExecutionMeta 是框架关切。
 *
 * @param triggerType 触发类型：CRON / FIXED_DELAY / FIXED_RATE / QUEUE / REQUEST
 * @param priority 队列任务优先级（0-9），其他触发类型为 null
 * @param bizId 关联业务 ID，方便排查时定位原始记录
 * @param context 执行参数快照 JSON
 * @author Kiro
 */
public record ExecutionMeta(String triggerType, Short priority, String bizId, String context) {

    public static ExecutionMeta scheduled(String triggerType) {
        return new ExecutionMeta(triggerType, null, null, null);
    }

    public static ExecutionMeta queue(short priority, String payload) {
        return new ExecutionMeta("QUEUE", priority, null, payload);
    }

    public static ExecutionMeta request() {
        return new ExecutionMeta("REQUEST", null, null, null);
    }

    public static ExecutionMeta of(String triggerType) {
        return new ExecutionMeta(triggerType, null, null, null);
    }
}
