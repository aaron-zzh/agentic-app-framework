package com.xuejiai.aaf.framework.intelligent.ai.music;

import com.xuejiai.aaf.framework.intelligent.core.AiCapability;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModel;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRoutingContext;

/**
 * 音乐生成服务接口。
 *
 * <p>基于阿里云百炼 fun-music-v1 模型，支持文本提示词或歌词生成音乐。
 */
public interface MusicGenerationService extends AiCapability {

    @Override
    default String capability() {
        return CapabilityRoutingContext.CAP_MUSIC_GEN;
    }

    @Override
    default String bizName() {
        return "音乐生成";
    }

    /** 提交音乐生成任务（非流式），返回生成结果。 */
    MusicResult generate(AiModel model, MusicRequest request);

    // === 请求/响应 Records ===

    /** 音乐生成请求。 */
    record MusicRequest(
            /** 提示词，模型根据提示词自动创作歌词并生成歌曲。与 lyrics 二选一。 */
            String prompt,
            /** 歌词内容。与 prompt 二选一，同时传入时仅 lyrics 生效。 */
            String lyrics,
            /** 演唱声音性别：male / female，默认 female。 */
            String gender,
            /** 音频格式：mp3 / wav，默认 mp3。 */
            String format) {}

    /** 音乐生成结果。 */
    record MusicResult(
            String requestId,
            /** 完整音频 URL（有效期 24 小时）。 */
            String audioUrl,
            /** 生成的歌词。 */
            String lyrics,
            /** 音频时长（秒）。 */
            Integer duration,
            /** 采样率。 */
            Integer sampleRate,
            /** 声道数。 */
            Integer channels) {}
}
