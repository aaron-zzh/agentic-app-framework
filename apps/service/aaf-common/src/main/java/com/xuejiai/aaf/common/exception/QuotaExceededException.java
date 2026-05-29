package com.xuejiai.aaf.common.exception;

import lombok.Getter;

/**
 * 权益额度不足异常。
 *
 * <p>当用户权益配额不足且无法通过积分充值补充时抛出。
 */
@Getter
public class QuotaExceededException extends RuntimeException {

    /** 权益编码 */
    private final String entitlementCode;

    /** 需要消耗的额度 */
    private final long required;

    /** 当前剩余额度 */
    private final long remain;

    public QuotaExceededException(String entitlementCode, long required, long remain) {
        super("权益额度不足: code=%s, required=%d, remain=%d".formatted(entitlementCode, required, remain));
        this.entitlementCode = entitlementCode;
        this.required = required;
        this.remain = remain;
    }
}
