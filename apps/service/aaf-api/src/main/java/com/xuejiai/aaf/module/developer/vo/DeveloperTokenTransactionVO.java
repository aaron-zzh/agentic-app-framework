package com.xuejiai.aaf.module.developer.vo;

import java.time.LocalDateTime;

public record DeveloperTokenTransactionVO(
        Long id,
        String type,
        Long amountTokens,
        Long balanceAfterTokens,
        String source,
        String bizId,
        LocalDateTime createTime) {}
