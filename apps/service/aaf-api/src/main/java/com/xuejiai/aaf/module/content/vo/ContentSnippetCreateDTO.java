package com.xuejiai.aaf.module.content.vo;

import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 创作片段创建请求。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "创建创作片段")
public record ContentSnippetCreateDTO(
        @NotBlank String name,
        String category,
        String content,
        List<String> referenceImageUrls,
        Map<String, Object> variableSlots,
        String projectTypeCode,
        Long brandProfileId,
        @NotNull Integer useCount,
        @NotNull Boolean isPublic) {}
