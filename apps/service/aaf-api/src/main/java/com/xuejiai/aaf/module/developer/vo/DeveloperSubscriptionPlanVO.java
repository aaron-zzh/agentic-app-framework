package com.xuejiai.aaf.module.developer.vo;

import java.time.LocalDateTime;

/** 开发者订阅套餐响应。 */
public record DeveloperSubscriptionPlanVO(
        Long id,
        String code,
        String name,
        Integer durationDays,
        Long price,
        Long includedTokens,
        Boolean allowManagedGateway,
        Boolean allowSubProxy,
        Integer maxProxyDepth,
        String status,
        Integer sortOrder,
        LocalDateTime createTime) {}
