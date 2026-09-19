package com.xuejiai.aaf.framework.engine.credit;

/** 跨模块成本投影使用的最小积分事实。 */
public record CreditTransactionView(
        Long id,
        String type,
        Long amount,
        String source,
        String bizType,
        String bizId,
        String batchType) {}
