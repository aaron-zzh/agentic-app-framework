package com.xuejiai.aaf.module.pay.vo;

/** 积分余额响应 */
public record CreditBalanceVO(
        Long userId, long balance, long frozen, long totalEarned, long totalSpent) {}
