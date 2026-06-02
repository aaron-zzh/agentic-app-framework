package com.xuejiai.aaf.module.billing.vo;

import java.time.LocalDateTime;

/** 用户权益额度视图 */
public record EntitlementQuotaVO(
        Long id,
        String code,
        String name,
        String type,
        String unit,
        long total,
        long used,
        long remain,
        LocalDateTime nextResetAt) {}
