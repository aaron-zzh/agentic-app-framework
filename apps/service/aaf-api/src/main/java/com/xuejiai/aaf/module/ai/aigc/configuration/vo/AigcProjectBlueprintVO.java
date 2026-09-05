package com.xuejiai.aaf.module.ai.aigc.configuration.vo;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/** 项目蓝图响应。 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "AIGC 项目蓝图信息")
public record AigcProjectBlueprintVO(
        Long id,
        Integer version,
        String code,
        String name,
        String projectTypeCode,
        String blueprintVersion,
        String productionMode,
        String description,
        String coverUrl,
        String status,
        Map<String, Object> slotTemplateSpec,
        Map<String, Object> relationSpec,
        Map<String, Object> actionSpec,
        Map<String, Object> deliverableSpec,
        Map<String, Object> processPolicy,
        List<String> briefFields) {}
