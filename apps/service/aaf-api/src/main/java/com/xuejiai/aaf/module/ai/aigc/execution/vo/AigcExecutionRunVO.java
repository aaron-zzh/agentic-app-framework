package com.xuejiai.aaf.module.ai.aigc.execution.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record AigcExecutionRunVO(
        Long id,
        Long projectId,
        Long objectId,
        Long parentRunId,
        String actionKey,
        String targetType,
        String targetRef,
        String status,
        String generationMode,
        String roleProfileCode,
        String selectedModelVersion,
        Map<String, Object> outputPayload,
        List<Long> taskIds,
        BigDecimal costCredits,
        Integer retryCount,
        String errorMessage,
        LocalDateTime startTime,
        LocalDateTime endTime,
        LocalDateTime createTime) {}
