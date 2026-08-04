package com.xuejiai.aaf.module.ai.aigc.brand.vo;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/** 品牌/IP 稳定身份响应。 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "AIGC 品牌/IP 资料")
public record AigcBrandProfileVO(
        Long id,
        Integer version,
        String name,
        String kind,
        String industry,
        Long currentVersionId,
        String status,
        LocalDateTime createTime,
        LocalDateTime updateTime) {}
