package com.xuejiai.aaf.framework.engine.credit;

/**
 * AI 同步调用积分结算的线程局部上下文。
 *
 * <p>用途：{@code AbstractAiServiceDecorator.creditCall} 在 {@code settleByUsage} 后将 creditTxId 写入此上下文，
 * 同步调用方（如 AigcTaskExecutor.submitSync）通过 {@link #takeLastCreditTxId()} 读取并回填到业务实体的关联字段（如 {@code
 * aigc_task.credit_tx_id}），用于后续失败退还。
 *
 * <p>每次 {@code creditCall} 进入时清空，保证不残留上次调用的状态。
 *
 * <p>仅适用同步链路；流式/异步链路不通过装饰器统一结算，应直接使用 {@code settleByUsageReturningTxId} 返回值。
 */
public final class CreditCallContext {

    private static final ThreadLocal<Long> LAST_CREDIT_TX_ID = new ThreadLocal<>();

    private CreditCallContext() {}

    /** 写入本次 settle 的 creditTxId。 */
    public static void setLastCreditTxId(Long creditTxId) {
        LAST_CREDIT_TX_ID.set(creditTxId);
    }

    /** 读取并清除当前线程最近一次 settle 的 creditTxId。 */
    public static Long takeLastCreditTxId() {
        Long id = LAST_CREDIT_TX_ID.get();
        LAST_CREDIT_TX_ID.remove();
        return id;
    }

    /** 清除当前线程的上下文（在 creditCall 进入时调用）。 */
    public static void clear() {
        LAST_CREDIT_TX_ID.remove();
    }
}
