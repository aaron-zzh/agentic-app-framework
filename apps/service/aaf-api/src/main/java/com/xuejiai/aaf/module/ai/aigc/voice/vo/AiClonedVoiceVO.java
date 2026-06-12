package com.xuejiai.aaf.module.ai.aigc.voice.vo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/** 声音复刻记录 Response VO。 */
public record AiClonedVoiceVO(
        @Schema(description = "ID") Long id,
        @Schema(description = "百炼平台 voice 名称") String voice,
        @Schema(description = "音色别名") String preferredName,
        @Schema(description = "绑定的全模态模型") String targetModel,
        @Schema(description = "复刻原始音频素材 ID") Long sourceAssetId,
        @Schema(description = "示例音频 URL（仅 SPEECH_TTS 类型）") String sampleAudioUrl,
        @Schema(description = "所属用户 ID") Long userId,
        @Schema(description = "创建时间") LocalDateTime createTime) {}
