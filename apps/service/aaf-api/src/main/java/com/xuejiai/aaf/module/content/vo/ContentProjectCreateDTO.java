package com.xuejiai.aaf.module.content.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 内容项目创建请求。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "创建内容项目")
public record ContentProjectCreateDTO(
        @NotBlank String name,
        @NotBlank String projectTypeCode,
        String blueprintCode,
        String blueprintVersion,
        String domainExtensionCode,
        String domainExtensionVersion,
        @NotBlank String productionMode,
        @NotBlank String generationMode,
        @NotBlank String status,
        String brief,
        String coverUrl,
        List<String> channels,
        Map<String, Object> configSnapshot,
        @NotNull Integer graphRevision,
        Long primaryBrandProfileId,
        Long assistantId,
        BigDecimal budgetLimit,
        @NotNull BigDecimal costUsed,
        LocalDateTime lastActiveTime) {}
