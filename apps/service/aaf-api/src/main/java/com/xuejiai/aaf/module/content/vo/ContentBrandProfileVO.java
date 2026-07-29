package com.xuejiai.aaf.module.content.vo;

import java.time.LocalDateTime;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 品牌/IP 资料响应。
 *
 * @author AaronZZH & Kiro
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "品牌/IP 资料信息")
public record ContentBrandProfileVO(
        Long id,
        Integer version,
        String name,
        String kind,
        String industry,
        String logoUrl,
        String positioning,
        String audience,
        String toneOfVoice,
        String visualStyle,
        String disclaimer,
        String forbiddenItems,
        Map<String, Object> rules,
        Map<String, Object> profileAssets,
        String profileVersion,
        String status,
        LocalDateTime createTime,
        LocalDateTime updateTime) {}
