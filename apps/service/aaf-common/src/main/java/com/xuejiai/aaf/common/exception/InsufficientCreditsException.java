package com.xuejiai.aaf.common.exception;

import lombok.Getter;

/**
 * 积分余额不足异常。
 *
 * <p>当用户积分余额 ≤ 0，无法继续调用 AI 能力时抛出。
 */
@Getter
public class InsufficientCreditsException extends RuntimeException {

    /** 用户 ID */
    private final Long userId;

    /** 当前可用余额 */
    private final long balance;

    public InsufficientCreditsException(Long userId, long balance) {
        super("积分余额不足: userId=%d, balance=%d".formatted(userId, balance));
        this.userId = userId;
        this.balance = balance;
    }
}
