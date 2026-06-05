package com.xuejiai.aaf.module.document.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** 创建文档 Request VO。 */
@Schema(description = "创建文档请求")
public record DocCreateDTO(
        @Schema(description = "文档标题", requiredMode = Schema.RequiredMode.REQUIRED, example = "需求规格")
                @NotBlank
                String title,
        @Schema(description = "文件路径", example = "docs/prd/feature-x.md") String filePath,
        @Schema(description = "文档内容（Markdown）") String content,
        @Schema(description = "文档类型", example = "spec") String docType,
        @Schema(description = "发布状态：draft / published", example = "draft") String publish) {}
