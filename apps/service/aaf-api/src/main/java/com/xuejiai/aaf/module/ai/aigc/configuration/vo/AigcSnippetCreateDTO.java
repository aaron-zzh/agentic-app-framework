package com.xuejiai.aaf.module.ai.aigc.configuration.vo;

import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;

/** 创建创作片段请求。 */
public record AigcSnippetCreateDTO(
        @NotBlank String name,
        String category,
        String content,
        List<Long> referenceMediaVersionIds,
        Map<String, Object> variableSlots,
        String projectTypeCode,
        Long brandProfileId,
        Integer useCount,
        Boolean isPublic) {}
