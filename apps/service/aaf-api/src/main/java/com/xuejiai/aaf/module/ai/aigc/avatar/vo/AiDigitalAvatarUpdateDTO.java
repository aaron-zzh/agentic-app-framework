package com.xuejiai.aaf.module.ai.aigc.avatar.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/** 数字人形象更新请求。 */
public record AiDigitalAvatarUpdateDTO(
        @Schema(description = "形象名称") String name,
        @Schema(description = "默认绑定的克隆音色") String defaultVoice,
        @Schema(description = "备注") String remark) {}
