package com.xuejiai.aaf.module.content.vo;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 行业扩展响应。
 *
 * @author AaronZZH & Kiro
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "行业扩展信息")
public record ContentDomainExtensionVO(
        Long id,
        String code,
        String name,
        String extensionVersion,
        String industry,
        String region,
        String language,
        String status) {}
