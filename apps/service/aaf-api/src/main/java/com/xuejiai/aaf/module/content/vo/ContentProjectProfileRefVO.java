package com.xuejiai.aaf.module.content.vo;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 项目资料引用响应。
 *
 * @author AaronZZH & Kiro
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "项目资料引用信息")
public record ContentProjectProfileRefVO(
        Long id,
        Long projectId,
        Long brandProfileId,
        String refScope,
        String profileVersion,
        String scopeNote,
        String brandProfileName) {}
