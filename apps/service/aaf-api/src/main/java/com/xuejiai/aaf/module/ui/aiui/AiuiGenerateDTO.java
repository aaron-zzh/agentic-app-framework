package com.xuejiai.aaf.module.ui.aiui;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * AI UI 生成请求 DTO。
 */
@Schema(description = "AI UI 生成请求")
public record AiuiGenerateDTO(
        @NotBlank @Schema(description = "自然语言描述") String prompt,
        @Schema(description = "上下文实体类型") String entityType
) {}
