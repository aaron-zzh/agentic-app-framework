package com.xuejiai.aaf.module.ai.aigc.project.vo;

import java.math.BigDecimal;

public record AigcProjectSummaryVO(
        Long projectId,
        long objectCount,
        long deliverableCount,
        long pendingConfirmCount,
        long blockedCount,
        long executionRunningCount,
        long candidateVersionCount,
        long adoptedVersionCount,
        long activeWorkCount,
        long publicationCount,
        long unpublishedPublicationCount,
        boolean completionReady,
        BigDecimal costUsed) {}
