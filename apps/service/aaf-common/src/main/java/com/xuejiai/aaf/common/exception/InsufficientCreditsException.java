package com.xuejiai.aaf.common.exception;

import lombok.Getter;

/**
 * 积分余额不足异常。
 *
 * <p>当用户积分（含透支额度）小于本次调用预估积分时抛出。
 *
 * <p>携带 {@code estimatedCost} 与 {@code overdraft}，方便日志和前端定位"差多少"。
 */
@Getter
public class InsufficientCreditsException extends RuntimeException {

    /** 用户 ID */
    private final Long userId;

    /** 当前可用余额 */
    private final long balance;

    /** 本次调用预估消耗积分（0 表示无法估算时按 1 处理） */
    private final long estimatedCost;

    /** 透支额度（来自 sys_config.credit_overdraft_limit） */
    private final long overdraft;

    public InsufficientCreditsException(Long userId, long balance) {
        this(userId, balance, 0L, 0L);
    }

    public InsufficientCreditsException(
            Long userId, long balance, long estimatedCost, long overdraft) {
        super(
                "积分余额不足: userId=%d, balance=%d, estimatedCost=%d, overdraft=%d, shortBy=%d"
                        .formatted(
                                userId,
                                balance,
                                estimatedCost,
                                overdraft,
                                Math.max(0, estimatedCost - balance - overdraft)));
        this.userId = userId;
        this.balance = balance;
        this.estimatedCost = estimatedCost;
        this.overdraft = overdraft;
    }
}
