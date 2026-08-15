package com.xuejiai.aaf.module.ai.aigc.configuration.vo;

import java.util.List;
import java.util.Map;

/** 创作片段响应。 */
public record AigcSnippetVO(
        Long id,
        Integer version,
        String name,
        String category,
        String content,
        List<Long> referenceMediaVersionIds,
        Map<String, Object> variableSlots,
        String projectTypeCode,
        Long brandProfileId,
        Integer useCount,
        Boolean isPublic,
        boolean builtin,
        boolean ownedByCurrentUser) {}
