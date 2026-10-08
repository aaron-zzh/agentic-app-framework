package com.xuejiai.aaf.module.digitalhuman.vo;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/** 预设音色响应。 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "预设音色")
public record PresetVoiceVO(
        @Schema(description = "音色 ID") Long id,
        @Schema(description = "名称") String name,
        @Schema(description = "描述") String description,
        @Schema(description = "性别") String gender,
        @Schema(description = "试听音频 URL") String sampleAudioUrl,
        @Schema(description = "提供商") String provider,
        @Schema(description = "外部音色 ID") String externalVoiceId,
        @Schema(description = "是否启用") Boolean isActive,
        @Schema(description = "显示顺序") Integer displayOrder) {}
