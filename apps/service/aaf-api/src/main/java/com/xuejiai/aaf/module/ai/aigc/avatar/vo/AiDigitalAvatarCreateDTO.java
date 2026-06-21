package com.xuejiai.aaf.module.ai.aigc.avatar.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record AiDigitalAvatarCreateDTO(
        @NotBlank @Schema(description = "形象名称") String name,
        @NotBlank @Schema(description = "形象图片 URL") String imageUrl,
        @Schema(description = "素材库关联 ID") Long sourceAssetId,
        @Schema(description = "默认绑定音色") String defaultVoice) {}
