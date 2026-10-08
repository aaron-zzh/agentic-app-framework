package com.xuejiai.aaf.module.digitalhuman.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 切换数字人音色命令。 */
@Schema(description = "切换音色")
public record DigitalHumanChangeVoiceDTO(
        @NotNull @Schema(description = "预设音色 ID", requiredMode = Schema.RequiredMode.REQUIRED)
                Long presetVoiceId,
        @Min(-500) @Max(500) @Schema(description = "语速（-500 ~ 500）", example = "0")
                Integer speechRate,
        @Min(-500) @Max(500) @Schema(description = "音调（-500 ~ 500）", example = "0")
                Integer pitchRate,
        @Min(0) @Max(100) @Schema(description = "音量（0 ~ 100）", example = "50") Integer volume) {}
