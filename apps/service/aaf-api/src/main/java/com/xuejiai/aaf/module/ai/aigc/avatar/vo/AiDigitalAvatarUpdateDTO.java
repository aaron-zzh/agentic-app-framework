package com.xuejiai.aaf.module.ai.aigc.avatar.vo;

import io.swagger.v3.oas.annotations.media.Schema;

public record AiDigitalAvatarUpdateDTO(
        @Schema(description = "形象名称") String name,
        @Schema(description = "默认绑定音色") String defaultVoice,
        @Schema(description = "备注") String remark) {}
