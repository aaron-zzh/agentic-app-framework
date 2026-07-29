package com.xuejiai.aaf.module.content.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 执行记录创建请求。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "创建执行记录")
public record ContentExecutionRunCreateDTO(
        @NotNull Long projectId,
        Long objectId,
        @NotBlank String actionKey,
        @NotBlank String targetType,
        String targetRef,
        @NotBlank String status,
        String generationMode,
        String roleProfileCode,
        String modelPolicyVersion,
        String selectedModelVersion,
        String skillDefinitionVersionId,
        String promptText,
        List<String> snippetRefs,
        List<String> attachmentRefs,
        List<Map<String, Object>> toolCalls,
        Map<String, Object> inputPayload,
        Map<String, Object> outputPayload,
        BigDecimal costCredits,
        String errorMessage,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Long aigcTaskId) {}
