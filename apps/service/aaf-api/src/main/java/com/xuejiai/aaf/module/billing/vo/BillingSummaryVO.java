package com.xuejiai.aaf.module.billing.vo;

/** 账单汇总 */
public record BillingSummaryVO(
        long totalConsumed,
        long totalRefilled,
        long creditBalance,
        int transactionCount) {
}
