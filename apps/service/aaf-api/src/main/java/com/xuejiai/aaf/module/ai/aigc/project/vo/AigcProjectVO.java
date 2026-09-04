package com.xuejiai.aaf.module.ai.aigc.project.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AigcProjectVO(
        Long id,
        Integer version,
        String name,
        String description,
        String projectTypeCode,
        String blueprintCode,
        String blueprintVersion,
        String domainExtensionCode,
        String domainExtensionVersion,
        String productionMode,
        String generationMode,
        String status,
        String brief,
        String prompt,
        Long coverMediaVersionId,
        String coverStatus,
        Long coverExecutionRunId,
        Long configSnapshotId,
        Integer graphRevision,
        Long primaryBrandProfileId,
        Long assistantId,
        BigDecimal budgetLimit,
        BigDecimal costUsed,
        LocalDateTime lastActiveTime,
        LocalDateTime createTime,
        LocalDateTime updateTime) {}
