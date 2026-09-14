package com.xuejiai.aaf.module.document.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 发布文档 Request VO。 */
@Schema(description = "发布文档请求")
public record DocPublishDTO(
        @Schema(description = "调用方确认的当前不可变文档版本 ID", requiredMode = Schema.RequiredMode.REQUIRED)
                @NotNull
                @Positive
                Long expectedCurrentVersionId) {}
