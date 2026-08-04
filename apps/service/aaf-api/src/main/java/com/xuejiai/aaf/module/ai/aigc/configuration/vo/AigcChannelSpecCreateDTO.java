package com.xuejiai.aaf.module.ai.aigc.configuration.vo;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 渠道规格创建请求。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "创建渠道规格")
public record AigcChannelSpecCreateDTO(
        @NotBlank String code,
        @NotBlank String name,
        @NotBlank String specVersion,
        String aspectRatio,
        Integer width,
        Integer height,
        Integer maxDurationSeconds,
        Map<String, Object> copyStructure,
        String requiredDisclaimers,
        String exportFormat,
        @NotNull Integer sortOrder) {}
