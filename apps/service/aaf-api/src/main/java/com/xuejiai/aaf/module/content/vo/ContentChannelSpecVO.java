package com.xuejiai.aaf.module.content.vo;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 渠道规格响应。
 *
 * @author AaronZZH & Kiro
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "渠道规格信息")
public record ContentChannelSpecVO(
        Long id,
        String code,
        String name,
        String specVersion,
        String aspectRatio,
        Integer width,
        Integer height,
        Integer maxDurationSeconds,
        String requiredDisclaimers,
        String exportFormat,
        Integer sortOrder,
        String status) {}
