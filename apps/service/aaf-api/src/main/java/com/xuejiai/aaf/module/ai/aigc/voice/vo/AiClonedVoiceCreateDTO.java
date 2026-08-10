package com.xuejiai.aaf.module.ai.aigc.voice.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 声音复刻创建请求。 */
public record AiClonedVoiceCreateDTO(
        @Schema(description = "复刻原始音频媒体版本 ID", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull
                Long sourceMediaVersionId,
        @Schema(
                        description = "音色别名（字母/数字/下划线，≤16字符）",
                        example = "myvoice",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank
                @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "仅允许字母、数字和下划线")
                @Size(max = 16, message = "别名不超过16个字符")
                String preferredName,
        @Schema(
                        description = "驱动音色的全模态模型",
                        example = "qwen3.5-omni-plus-realtime",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank
                String targetModel,
        @Schema(description = "音频对应的文本（可选，用于服务端校验）") String text,
        @Schema(description = "音频语种（可选），如 zh / en / Sichuan") String language) {}
