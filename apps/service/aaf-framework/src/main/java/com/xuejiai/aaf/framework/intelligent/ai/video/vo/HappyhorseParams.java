package com.xuejiai.aaf.framework.intelligent.ai.video.vo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.intelligent.ai.video.VideoGenerationService.*;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModel;

/**
 * HappyHorse 系列模型 API 请求参数——从 {@link VideoRequest} + {@link VideoConfig} 构建，构造时完成校验。
 *
 * <p>覆盖模型：happyhorse-1.0-t2v / i2v / r2v / video-edit。
 *
 * <p>{@link #toInput()} 生成 DashScope API 的 {@code input} 结构；{@link #toParameters()} 生成 {@code
 * parameters} 结构。
 */
public final class HappyhorseParams {

    private final String modelName;
    private final String prompt;
    private final VideoRequest.ImageMode imageMode;
    private final String imageUrl;
    private final List<String> referenceImageUrls;
    private final String videoUrl;
    private final String resolution;
    private final String ratio;
    private final Integer duration;
    private final Integer seed;
    private final String audioSetting;

    private HappyhorseParams(VideoRequest req, AiModel model) {
        this.modelName = model.getModelName();
        this.prompt = req.prompt();
        this.imageMode = req.imageMode() != null ? req.imageMode() : VideoRequest.ImageMode.T2V;
        this.imageUrl = req.imageUrl();
        this.referenceImageUrls = req.referenceImageUrls();
        this.resolution = req.resolution();
        this.ratio = req.ratio();
        this.duration = req.duration();
        this.seed = req.seed();
        this.audioSetting = null; // VideoRequest 暂无 audioSetting，video-edit 通过扩展字段传入
        this.videoUrl = null;
    }

    private HappyhorseParams(VideoEditApiRequest req, AiModel model) {
        this.modelName = model != null ? model.getModelName() : req.model();
        this.prompt = req.prompt();
        this.imageMode = VideoRequest.ImageMode.T2V; // video-edit 不走 imageMode 路由
        this.imageUrl = null;
        this.referenceImageUrls = req.referenceImageUrls();
        this.videoUrl = req.videoUrl();
        this.resolution = req.resolution();
        this.ratio = null;
        this.duration = null;
        this.seed = req.seed();
        this.audioSetting = req.audioSetting();
    }

    /** 从统一视频请求构建，会依据 VideoConfig 校验。 */
    public static HappyhorseParams of(VideoRequest req, AiModel model) {
        var config = model.getVideoConfigParsed();
        var params = new HappyhorseParams(req, model);
        params.validate(config);
        return params;
    }

    /** 从视频编辑请求构建。 */
    public static HappyhorseParams ofEdit(VideoEditApiRequest req, AiModel model) {
        var config = model != null ? model.getVideoConfigParsed() : null;
        var params = new HappyhorseParams(req, model);
        params.validateEdit(config);
        return params;
    }

    // ========== 校验 ==========

    private void validate(VideoConfig cfg) {
        if (prompt == null || prompt.isBlank()) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "prompt 不能为空");
        }
        if (cfg == null) return;

        if (resolution != null
                && cfg.resolutions() != null
                && !cfg.resolutions().contains(resolution)) {
            throw new BusinessException(
                    GlobalErrorCode.BAD_REQUEST,
                    "不支持的分辨率: " + resolution + "，可选: " + cfg.resolutions());
        }
        if (ratio != null && cfg.ratios() != null && !cfg.ratios().contains(ratio)) {
            throw new BusinessException(
                    GlobalErrorCode.BAD_REQUEST, "不支持的比例: " + ratio + "，可选: " + cfg.ratios());
        }
        if (duration != null && cfg.maxDuration() != null && duration > cfg.maxDuration()) {
            throw new BusinessException(
                    GlobalErrorCode.BAD_REQUEST, "时长超出上限 " + cfg.maxDuration() + " 秒");
        }
        if (imageMode == VideoRequest.ImageMode.REFERENCE) {
            var refs =
                    referenceImageUrls != null
                            ? referenceImageUrls
                            : (imageUrl != null ? List.of(imageUrl) : List.<String>of());
            if (cfg.maxReferenceImages() != null && refs.size() > cfg.maxReferenceImages()) {
                throw new BusinessException(
                        GlobalErrorCode.BAD_REQUEST, "参考图数量超出上限 " + cfg.maxReferenceImages());
            }
        }
    }

    private void validateEdit(VideoConfig cfg) {
        if (prompt == null || prompt.isBlank()) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "prompt 不能为空");
        }
        if (videoUrl == null || videoUrl.isBlank()) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "视频编辑必须传入 videoUrl");
        }
        if (cfg == null) return;
        if (audioSetting != null
                && cfg.supportsAudioSetting()
                && !cfg.audioSetting().contains(audioSetting)) {
            throw new BusinessException(
                    GlobalErrorCode.BAD_REQUEST,
                    "不支持的 audioSetting: " + audioSetting + "，可选: " + cfg.audioSetting());
        }
    }

    // ========== 生成请求体 ==========

    /** 生成 DashScope API {@code input} 结构。 */
    public Map<String, Object> toInput() {
        var input = new HashMap<String, Object>();
        if (prompt != null) input.put("prompt", prompt);

        var mediaList = buildMediaList();
        if (!mediaList.isEmpty()) input.put("media", mediaList);
        return input;
    }

    /** 生成 DashScope API {@code parameters} 结构。 */
    public Map<String, Object> toParameters() {
        var params = new HashMap<String, Object>();
        if (resolution != null) params.put("resolution", resolution);
        if (ratio != null) params.put("ratio", ratio);
        if (duration != null) params.put("duration", duration);
        if (seed != null) params.put("seed", seed);
        if (audioSetting != null) params.put("audio_setting", audioSetting);
        params.put("watermark", false);
        return params;
    }

    public String modelName() {
        return modelName;
    }

    // ========== 内部 ==========

    private List<Map<String, String>> buildMediaList() {
        var list = new ArrayList<Map<String, String>>();
        if (videoUrl != null) {
            // video-edit 模式
            list.add(Map.of("type", "video", "url", videoUrl));
            if (referenceImageUrls != null) {
                for (var url : referenceImageUrls) {
                    list.add(Map.of("type", "reference_image", "url", url));
                }
            }
        } else if (imageMode == VideoRequest.ImageMode.FIRST_FRAME && imageUrl != null) {
            list.add(Map.of("type", "first_frame", "url", imageUrl));
        } else if (imageMode == VideoRequest.ImageMode.REFERENCE) {
            var refs =
                    referenceImageUrls != null && !referenceImageUrls.isEmpty()
                            ? referenceImageUrls
                            : (imageUrl != null ? List.of(imageUrl) : List.<String>of());
            for (var url : refs) {
                list.add(Map.of("type", "reference_image", "url", url));
            }
        }
        return list;
    }
}
