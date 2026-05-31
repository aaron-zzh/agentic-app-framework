package com.xuejiai.aaf.module.developer.vo;

public record DeveloperTokenAccountVO(
        Long developerId,
        long balanceTokens,
        long frozenTokens,
        long totalEarnedTokens,
        long totalSpentTokens) {}
