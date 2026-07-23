package com.xuejiai.aaf.module.billing.vo;

import java.time.LocalDateTime;

import com.xuejiai.aaf.framework.crud.dto.ResourceRefDTO;

public record WalletTransactionVO(
        Long id,
        ResourceRefDTO user,
        Long accountId,
        String type,
        Long amount,
        Long balanceAfter,
        String source,
        String category,
        String bizType,
        String bizId,
        String batchType,
        LocalDateTime expireAt,
        Long remain,
        String remark,
        LocalDateTime createTime) {}
