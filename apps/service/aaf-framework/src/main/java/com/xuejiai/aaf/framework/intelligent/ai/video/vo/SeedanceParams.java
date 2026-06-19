package com.xuejiai.aaf.framework.intelligent.ai.video.vo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModel;

/**
 * Doubao Seedance 系列模型 API 请求参数——从 Controller DTO + {@link VideoConfig} 构建，构造时完成校验。
 *
 * <p>API 结构与 HappyHorse 不同：请求体为扁平结构，媒体通过 {@code content} 数组传入。
 *
 * <p>{@link #toBody()} 直接生成完整的 Volcengine 方舟请求体。
 */
public final class SeedanceParams {

    private final String modelName;
    private final String prompt;
    private final List<String> referenceImages;
    private final List<String> referenceVideos;
    private final List<String> referenceAudios;
    private final String ratio;
    private final Integer duration;
    private final boolean generateAudio;

    private SeedanceParams(
            String modelName,
            String prompt,
            List<String> referenceImages,
            List<String> referenceVideos,
            List<String> referenceAudios,
            String ratio,
            Integer duration,
            boolean generateAudio) {
        this.modelName = modelName;
        this.prompt = prompt;
        this.referenceImages = referenceImages;
        this.referenceVideos = referenceVideos;
        this.referenceAudios = referenceAudios;
        this.ratio = ratio;
        this.duration = duration;
        this.generateAudio = generateAudio;
    }

    /**
     * 工厂方法：构建并校验 SeedanceParams。
     *
     * @param model 已 resolve 的 AiModel（用于取 modelName + videoConfig）
     * @param prompt 文本描述
     * @param refImages 参考图 URL 列表
     * @param refVideos 参考视频 URL 列表
     * @param refAudios 参考音频 URL 列表
     * @param ratio 画面比例
     * @param duration 时长（秒）
     * @param generateAudio 是否生成配套音频
     */
    public static SeedanceParams of(
            AiModel model,
            String prompt,
            List<String> refImages,
            List<String> refVideos,
            List<String> refAudios,
            String ratio,
            Integer duration,
            boolean generateAudio) {
        var params =
                new SeedanceParams(
                        model.getModelName(),
                        prompt,
                        refImages,
                        refVideos,
                        refAudios,
                        ratio,
                        duration,
                        generateAudio);
        params.validate(model.getVideoConfigParsed());
        return params;
    }

    // ========== 校验 ==========

    private void validate(VideoConfig cfg) {
        if (prompt == null || prompt.isBlank()) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "prompt 不能为空");
        }
        if (cfg == null) return;

        if (ratio != null && cfg.ratios() != null && !cfg.ratios().contains(ratio)) {
            throw new BusinessException(
                    GlobalErrorCode.BAD_REQUEST, "不支持的比例: " + ratio + "，可选: " + cfg.ratios());
        }
        if (duration != null && cfg.maxDuration() != null && duration > cfg.maxDuration()) {
            throw new BusinessException(
                    GlobalErrorCode.BAD_REQUEST, "时长超出上限 " + cfg.maxDuration() + " 秒");
        }
        if (referenceImages != null
                && cfg.maxReferenceImages() != null
                && referenceImages.size() > cfg.maxReferenceImages()) {
            throw new BusinessException(
                    GlobalErrorCode.BAD_REQUEST, "参考图数量超出上限 " + cfg.maxReferenceImages());
        }
        if (referenceVideos != null
                && cfg.maxReferenceVideos() != null
                && referenceVideos.size() > cfg.maxReferenceVideos()) {
            throw new BusinessException(
                    GlobalErrorCode.BAD_REQUEST, "参考视频数量超出上限 " + cfg.maxReferenceVideos());
        }
        if (referenceAudios != null
                && cfg.maxReferenceAudios() != null
                && referenceAudios.size() > cfg.maxReferenceAudios()) {
            throw new BusinessException(
                    GlobalErrorCode.BAD_REQUEST, "参考音频数量超出上限 " + cfg.maxReferenceAudios());
        }
        if (generateAudio && !cfg.supportsGenerateAudio()) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "该模型不支持生成配套音频");
        }
    }

    // ========== 生成请求体 ==========

    /** 生成 Volcengine 方舟 API 完整请求体。 */
    public Map<String, Object> toBody() {
        var content = new ArrayList<Map<String, Object>>();
        content.add(Map.of("type", "text", "text", prompt));

        if (referenceImages != null) {
            for (var url : referenceImages) {
                content.add(
                        Map.of(
                                "type",
                                "image_url",
                                "image_url",
                                Map.of("url", url),
                                "role",
                                "reference_image"));
            }
        }
        if (referenceVideos != null) {
            for (var url : referenceVideos) {
                content.add(
                        Map.of(
                                "type",
                                "video_url",
                                "video_url",
                                Map.of("url", url),
                                "role",
                                "reference_video"));
            }
        }
        if (referenceAudios != null) {
            for (var url : referenceAudios) {
                content.add(
                        Map.of(
                                "type",
                                "audio_url",
                                "audio_url",
                                Map.of("url", url),
                                "role",
                                "reference_audio"));
            }
        }

        var body = new HashMap<String, Object>();
        body.put("model", modelName);
        body.put("content", content);
        body.put("watermark", false);
        if (ratio != null) body.put("ratio", ratio);
        if (duration != null) body.put("duration", duration);
        if (generateAudio) body.put("generate_audio", true);
        return body;
    }

    public String modelName() {
        return modelName;
    }
}
