package com.xuejiai.aaf.module.ai.aigc.project.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectCoverStatus;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectLifecycle;

public record AigcProjectVO(
        Long id,
        Integer version,
        String name,
        String description,
        String background,
        String targetAudience,
        List<String> requirements,
        List<String> constraints,
        List<String> keyFacts,
        List<String> keyMessages,
        String styleGuidance,
        List<String> successCriteria,
        Map<String, Object> deliveryRequirements,
        String locale,
        String additionalContext,
        String projectTypeCode,
        String blueprintCode,
        String blueprintVersion,
        String productionMode,
        String generationMode,
        AigcProjectLifecycle status,
        String prompt,
        Long coverMediaVersionId,
        AigcProjectCoverStatus coverStatus,
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
