package com.xuejiai.aaf.module.billing.api;

/** 订阅并发窗口只读契约；并发槽位是瞬时上限，不走权益累计扣减。 */
public interface ParallelTaskQuotaApi {

    /**
     * 读取用户当前订阅授予的并行任务上限。
     *
     * <p>返回 {@code -1} 表示不限制；其余返回值为正数。权益记录缺失或额度非正时返回 {@code 1}——并发窗口是"同时最多几个"， 取 0
     * 会让排队任务永远无法启动，因此退化为串行而不是完全停机。本方法只读当前 {@code total}，不消费也不修改额度。
     */
    long parallelTaskLimit(Long userId);
}
