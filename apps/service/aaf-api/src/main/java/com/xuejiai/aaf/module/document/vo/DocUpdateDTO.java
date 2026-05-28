package com.xuejiai.aaf.module.document.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** 更新文档 Request VO。 */
@Schema(description = "更新文档请求")
public record DocUpdateDTO(
        @Schema(description = "文档内容（Markdown）", requiredMode = Schema.RequiredMode.REQUIRED)
                @NotNull
                String content) {}
