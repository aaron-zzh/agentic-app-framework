package com.xuejiai.aaf.module.content.vo;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 项目类型响应。
 *
 * @author AaronZZH & Kiro
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "项目类型信息")
public record ContentProjectTypeVO(
        Long id,
        String code,
        String name,
        String icon,
        String description,
        String briefPlaceholder,
        List<String> defaultChannels,
        String defaultProductionMode,
        Boolean quickEntry,
        Boolean builtin,
        Integer sortOrder,
        String status) {}
