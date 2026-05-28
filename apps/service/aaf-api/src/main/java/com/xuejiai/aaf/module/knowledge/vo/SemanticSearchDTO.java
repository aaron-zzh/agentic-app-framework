package com.xuejiai.aaf.module.knowledge.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 语义搜索请求 DTO。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "语义搜索请求")
public record SemanticSearchDTO(
        @Schema(description = "查询文本", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank
                String query,
        @Schema(description = "返回结果数量", example = "5") Integer topK) {}
