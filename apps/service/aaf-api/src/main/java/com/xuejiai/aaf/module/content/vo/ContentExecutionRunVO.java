package com.xuejiai.aaf.module.content.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 执行记录响应。
 *
 * @author AaronZZH & Kiro
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "执行记录信息")
public record ContentExecutionRunVO(
        Long id,
        Long projectId,
        Long objectId,
        String actionKey,
        String targetType,
        String targetRef,
        String status,
        String generationMode,
        String roleProfileCode,
        String selectedModelVersion,
        BigDecimal costCredits,
        String errorMessage,
        LocalDateTime startTime,
        LocalDateTime endTime,
        LocalDateTime createTime) {}
