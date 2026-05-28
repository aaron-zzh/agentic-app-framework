package com.xuejiai.aaf.module.knowledge.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 更新段落请求 DTO。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "更新段落请求")
public record SegmentUpdateDTO(
        @Schema(description = "段落内容", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank
                String content) {}
