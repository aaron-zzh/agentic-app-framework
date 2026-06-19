package com.xuejiai.aaf.framework.intelligent.ai.video.vo;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 视频生成能力配置——对应 ai_model.video_config JSONB 字段，与前端 VideoConfig TS 接口保持一致。
 *
 * <p>字段存在（非 null）= 支持该参数；字段为 null = 不支持或无限制。
 *
 * @param resolutions 支持的分辨率列表，如 ["720p","1080p"]
 * @param ratios 支持的宽高比列表，如 ["16:9","9:16","1:1"]；null 表示不支持或跟随输入
 * @param durations 支持的时长档位（秒），如 [5, 10]；null 表示连续取值
 * @param maxDuration 最大时长（秒），null 表示无限制
 * @param seed 是否支持随机种子
 * @param watermark 是否支持水印开关（false=可关闭，true=强制有水印无法关闭）
 * @param audioSetting 支持的音频控制选项列表，如 ["auto","origin"]；null 表示不支持
 * @param generateAudio 是否支持生成配套音频（doubao seedance 专属）
 * @param promptExtend 是否支持提示词扩写
 * @param maxReferenceImages 最多参考图数量（r2v 模式），null 表示不支持
 * @param maxReferenceVideos 最多参考视频数量（seedance 专属），null 表示不支持
 * @param maxReferenceAudios 最多参考音频数量（seedance 专属），null 表示不支持
 * @param modes 支持的生成模式列表，如 ["t2v","i2v","r2v","video-edit"]
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record VideoConfig(
        List<String> resolutions,
        List<String> ratios,
        List<Integer> durations,
        Integer maxDuration,
        Boolean seed,
        Boolean watermark,
        List<String> audioSetting,
        Boolean generateAudio,
        Boolean promptExtend,
        Integer maxReferenceImages,
        Integer maxReferenceVideos,
        Integer maxReferenceAudios,
        List<String> modes) {

    public boolean supportsSeed() {
        return Boolean.TRUE.equals(seed);
    }

    public boolean supportsWatermarkToggle() {
        return Boolean.TRUE.equals(watermark);
    }

    public boolean supportsAudioSetting() {
        return audioSetting != null && !audioSetting.isEmpty();
    }

    public boolean supportsGenerateAudio() {
        return Boolean.TRUE.equals(generateAudio);
    }

    public boolean supportsPromptExtend() {
        return Boolean.TRUE.equals(promptExtend);
    }

    public boolean supportsReferenceImage() {
        return maxReferenceImages != null && maxReferenceImages > 0;
    }

    public boolean supportsReferenceVideo() {
        return maxReferenceVideos != null && maxReferenceVideos > 0;
    }

    public boolean supportsReferenceAudio() {
        return maxReferenceAudios != null && maxReferenceAudios > 0;
    }

    public boolean supportsMode(String mode) {
        return modes != null && modes.contains(mode);
    }
}
