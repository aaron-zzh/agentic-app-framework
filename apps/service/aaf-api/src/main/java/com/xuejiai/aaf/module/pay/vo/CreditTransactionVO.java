package com.xuejiai.aaf.module.pay.vo;

import java.time.LocalDateTime;

/** 积分流水响应 */
public record CreditTransactionVO(
        Long id,
        String type,
        Long amount,
        Long balanceAfter,
        String source,
        String category,
        String remark,
        String bizId,
        LocalDateTime createTime) {}
