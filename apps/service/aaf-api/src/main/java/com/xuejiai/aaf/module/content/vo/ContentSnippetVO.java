package com.xuejiai.aaf.module.content.vo;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 创作片段响应。
 *
 * @author AaronZZH & Kiro
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "创作片段信息")
public record ContentSnippetVO(
        Long id,
        String name,
        String category,
        String content,
        List<String> referenceImageUrls,
        Map<String, Object> variableSlots,
        String projectTypeCode,
        Long brandProfileId,
        Integer useCount,
        Boolean isPublic) {}
