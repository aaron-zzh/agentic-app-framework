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
        String status,
        Map<String, Object> objectSpec,
        Map<String, Object> relationSpec,
        Map<String, Object> deliverableSpec,
        List<String> actionKeys,
        List<String> confirmationGates,
        List<String> briefFields) {}
