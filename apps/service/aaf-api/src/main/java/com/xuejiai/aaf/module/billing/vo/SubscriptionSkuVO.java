package com.xuejiai.aaf.module.billing.vo;

import java.time.LocalDateTime;

import com.xuejiai.aaf.framework.crud.dto.ResourceRefDTO;

public record SubscriptionSkuVO(
        Long id,
        ResourceRefDTO plan,
        String skuCode,
        String billingCycle,
        Integer cycleMonths,
        Long price,
        Long marketPrice,
        String status,
        Integer sort,
        String ext,
        LocalDateTime createTime,
        LocalDateTime updateTime) {}
