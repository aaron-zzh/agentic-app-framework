package com.xuejiai.aaf.module.pay.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 对账记录响应 */
public record ReconcileRecordVO(
        Long id,
        LocalDate reconcileDate,
        String channelCode,
        Integer totalCount,
        Integer matchedCount,
        Integer mismatchCount,
        Integer status,
        String diffDetails,
        Long totalIncome,
        Long totalRefund,
        Long totalFee,
        LocalDateTime createTime) {}
