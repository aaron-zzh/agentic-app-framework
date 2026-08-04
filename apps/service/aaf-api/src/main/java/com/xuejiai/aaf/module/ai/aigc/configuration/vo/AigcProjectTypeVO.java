package com.xuejiai.aaf.module.ai.aigc.configuration.vo;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/** 项目类型响应。 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "AIGC 项目类型信息")
public record AigcProjectTypeVO(
        Long id,
        Integer version,
        String code,
        String name,
        String icon,
        String description,
        String briefPlaceholder,
        String definitionVersion,
        List<String> defaultChannels,
        String defaultProductionMode,
        Boolean quickEntry,
        Boolean builtin,
        Integer sortOrder,
        String status) {}
