package com.xuejiai.aaf.module.ai.aigc.avatar.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AiDigitalAvatarCreateDTO(
        @NotBlank @Schema(description = "形象名称") String name,
        @NotNull @Schema(description = "形象图片媒体版本 ID") Long imageMediaVersionId,
        @Schema(description = "原始形象素材媒体版本 ID") Long sourceMediaVersionId,
        @Schema(description = "默认绑定音色") String defaultVoice) {}
