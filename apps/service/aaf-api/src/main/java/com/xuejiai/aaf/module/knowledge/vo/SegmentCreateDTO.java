package com.xuejiai.aaf.module.knowledge.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 创建段落请求 DTO。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "创建段落请求")
public record SegmentCreateDTO(
        @Schema(description = "所属文档编号", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull
                Long documentId,
        @Schema(description = "段落内容", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank
                String content,
        @Schema(description = "段落位置") Integer position) {}
