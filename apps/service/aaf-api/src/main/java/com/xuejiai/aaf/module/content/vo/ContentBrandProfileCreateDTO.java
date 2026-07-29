package com.xuejiai.aaf.module.content.vo;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 品牌/IP 资料创建请求。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "创建品牌/IP 资料")
public record ContentBrandProfileCreateDTO(
        @NotBlank String name,
        @NotBlank String kind,
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
        @NotBlank String status) {}
