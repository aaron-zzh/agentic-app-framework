package com.xuejiai.aaf.module.billing.vo;

import java.time.LocalDateTime;

import com.xuejiai.aaf.framework.crud.dto.ResourceRefDTO;

/** 用户权益额度视图。 */
public record EntitlementQuotaVO(
        Long id,
        ResourceRefDTO user,
        ResourceRefDTO entitlement,
        String code,
        String name,
        String type,
        String unit,
        Long total,
        Long used,
        Long remain,
        LocalDateTime nextResetAt) {}
