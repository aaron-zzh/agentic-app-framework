package com.xuejiai.aaf.module.digitalhuman.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 数字人音色配置。
 *
 * <p>未传的供应商、语速、音调、音量使用默认值；数值越界在构造时拒绝，保证创建、更新与换音色共用同一规则。
 */
@Schema(description = "音色配置")
public record DigitalHumanVoiceConfig(
        @Schema(description = "提供商", example = "aliyun") String provider,
        @NotNull @Schema(description = "预设音色 ID", requiredMode = Schema.RequiredMode.REQUIRED)
                Long presetVoiceId,
        @Schema(description = "音色名称，未传时取预设音色名称") String voiceName,
        @Schema(description = "语速（-500 ~ 500）", example = "0") Integer speechRate,
        @Schema(description = "音调（-500 ~ 500）", example = "0") Integer pitchRate,
        @Schema(description = "音量（0 ~ 100）", example = "50") Integer volume) {

    public static final String DEFAULT_PROVIDER = "aliyun";

    public DigitalHumanVoiceConfig {
        provider = provider == null ? DEFAULT_PROVIDER : provider;
        speechRate = speechRate == null ? 0 : speechRate;
        pitchRate = pitchRate == null ? 0 : pitchRate;
        volume = volume == null ? 50 : volume;
        requireRange("speechRate", speechRate, -500, 500);
        requireRange("pitchRate", pitchRate, -500, 500);
        requireRange("volume", volume, 0, 100);
    }

    private static void requireRange(String field, int value, int min, int max) {
        if (value < min || value > max) {
            throw new IllegalArgumentException("%s 必须在 %d ~ %d 之间".formatted(field, min, max));
        }
    }
}
