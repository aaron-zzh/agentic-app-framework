package com.xuejiai.aaf.module.ai.aigc.execution.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.xuejiai.aaf.module.ai.aigc.execution.api.AigcExecutionRunStatus;

public record AigcExecutionRunVO(
        Long id,
        Long projectId,
        Long objectId,
        Long parentExecutionRunId,
        Long rootExecutionRunId,
        String runKind,
        String workflowNodeKey,
        Long executionSubmissionId,
        Long executionReservationId,
        Long targetGraphRevision,
        List<Long> frozenProjectObjectIds,
        Map<String, Object> effectiveInput,
        Long bindingVersionId,
        String actionKey,
        String targetType,
        String targetRef,
        AigcExecutionRunStatus status,
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
