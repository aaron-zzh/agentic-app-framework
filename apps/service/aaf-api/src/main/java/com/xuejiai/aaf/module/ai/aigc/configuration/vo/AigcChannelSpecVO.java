package com.xuejiai.aaf.module.ai.aigc.configuration.vo;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/** 渠道规格响应。 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "AIGC 渠道规格信息")
public record AigcChannelSpecVO(
        Long id,
        Integer version,
        String code,
        String name,
        String specVersion,
        String aspectRatio,
        Integer width,
        Integer height,
        Integer maxDurationSeconds,
        Map<String, Object> copyStructure,
        String requiredDisclaimers,
        String exportFormat,
        Integer sortOrder,
        String status) {}
