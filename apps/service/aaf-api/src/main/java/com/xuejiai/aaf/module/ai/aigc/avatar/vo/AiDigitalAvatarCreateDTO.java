package com.xuejiai.aaf.module.ai.aigc.avatar.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** 数字人形象创建请求。 */
public record AiDigitalAvatarCreateDTO(
        @Schema(description = "形象名称", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank
                String name,
        @Schema(description = "形象图片公网 URL", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank
                String imageUrl,
        @Schema(description = "图片来源素材 ID（可选）") Long sourceAssetId,
        @Schema(description = "默认绑定的克隆音色（可选）") String defaultVoice) {}
