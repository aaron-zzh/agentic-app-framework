package com.xuejiai.aaf.module.pay.vo;

import java.time.LocalDateTime;

/** 积分转 Token 规则响应 */
public record CreditTokenRuleVO(
        Long id,
        String name,
        Long creditAmount,
        Long tokenAmount,
        String status,
        Integer priority,
        LocalDateTime effectiveFrom,
        LocalDateTime effectiveTo,
        LocalDateTime createTime) {}
