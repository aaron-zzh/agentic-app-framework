package com.xuejiai.aaf.module.ai.aigc.brand.vo;

import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/** 创建新的品牌资料草稿版本。 */
public record AigcBrandProfileVersionCreateDTO(
        @NotNull @PositiveOrZero Integer expectedProfileVersion,
        String positioning,
        String audience,
        String toneOfVoice,
        String visualStyle,
        String disclaimer,
        String forbiddenItems,
        Map<String, Object> rules,
        List<@Positive Long> mediaVersionIds,
        List<@Positive Long> documentVersionIds) {}
