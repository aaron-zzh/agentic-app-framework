package com.xuejiai.aaf.module.pay.vo;

import java.time.LocalDateTime;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 积分转 Token 规则创建/更新请求 */
public record CreditTokenRuleDTO(
        @NotBlank String name,
        @NotNull @Min(1) Long creditAmount,
        @NotNull @Min(1) Long tokenAmount,
        String status,
        Integer priority,
        LocalDateTime effectiveFrom,
        LocalDateTime effectiveTo) {}
