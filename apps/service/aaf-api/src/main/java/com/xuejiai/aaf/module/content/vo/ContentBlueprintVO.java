package com.xuejiai.aaf.module.content.vo;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 项目蓝图响应。
 *
 * @author AaronZZH & Kiro
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "项目蓝图信息")
public record ContentBlueprintVO(
        Long id,
        String code,
        String name,
        String projectTypeCode,
        String blueprintVersion,
        String productionMode,
        String description,
        String status) {}
