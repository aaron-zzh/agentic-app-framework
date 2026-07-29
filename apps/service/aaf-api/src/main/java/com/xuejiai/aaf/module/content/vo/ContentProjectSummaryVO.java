package com.xuejiai.aaf.module.content.vo;

import java.math.BigDecimal;

/**
 * 内容项目概览响应。
 *
 * @author AaronZZH & Kiro
 */
public record ContentProjectSummaryVO(
        Long projectId,
        long objectCount,
        long deliverableCount,
        long pendingConfirmCount,
        long blockedCount,
        long executionRunningCount,
        BigDecimal costUsed) {}
