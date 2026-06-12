package com.xuejiai.aaf.module.ai.aigc.avatar.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** 数字人视频生成请求。 */
public record AvatarVideoGenerateDTO(
        @Schema(description = "驱动音频公网 URL", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank
                String audioUrl,
        @Schema(description = "风格：speech（说话）/ sing（唱歌）/ perform（表演），默认 speech") String style,
        @Schema(description = "分辨率：480P / 720P，默认 480P") String resolution) {}
