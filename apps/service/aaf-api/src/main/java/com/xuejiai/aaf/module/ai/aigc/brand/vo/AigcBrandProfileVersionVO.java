package com.xuejiai.aaf.module.ai.aigc.brand.vo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 品牌/IP 资料不可变版本响应。 */
public record AigcBrandProfileVersionVO(
        Long id,
        Integer version,
        Long brandProfileId,
        Integer versionNo,
        String positioning,
        String audience,
        String toneOfVoice,
        String visualStyle,
        String disclaimer,
        String forbiddenItems,
        Map<String, Object> rules,
        String status,
        List<Long> mediaVersionIds,
        List<Long> documentVersionIds,
        LocalDateTime createTime) {}
