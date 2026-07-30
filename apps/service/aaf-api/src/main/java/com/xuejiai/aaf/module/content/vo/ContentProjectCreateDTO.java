package com.xuejiai.aaf.module.content.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

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
        String domainExtensionCode,
        @NotBlank String productionMode,
        @NotBlank String generationMode,
        @NotBlank String status,
        String brief,
        String coverUrl,
        List<String> channels,
        Long primaryBrandProfileId,
        Long assistantId,
        BigDecimal budgetLimit,
        LocalDateTime lastActiveTime) {}
