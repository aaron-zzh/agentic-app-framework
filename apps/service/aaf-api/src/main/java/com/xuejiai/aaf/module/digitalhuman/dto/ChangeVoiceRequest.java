package com.xuejiai.aaf.module.digitalhuman.dto;

import jakarta.validation.constraints.NotNull;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "切换音色请求")
public class ChangeVoiceRequest {

    @NotNull(message = "预设音色 ID 不能为空")
    @Schema(description = "预设音色 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long presetVoiceId;

    @Schema(description = "语速（-500 ~ 500）", example = "0")
    private Integer speechRate = 0;

    @Schema(description = "音调（-500 ~ 500）", example = "0")
    private Integer pitchRate = 0;

    @Schema(description = "音量（0 ~ 100）", example = "50")
    private Integer volume = 50;
}
