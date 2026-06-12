package com.xuejiai.aaf.framework.intelligent.ai.image.vo;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;

/** Gemini 文生图参数——从 {@link ImageRequest} + {@link ImageConfig} 构建，构造时完成校验。 */
public final class GeminiGenerateParams {

    private final String prompt;
    private final String aspectRatio;
    private final String sizePreset;
    private final String quality;
    private final int imageCount;
    private final Integer seed;

    private GeminiGenerateParams(ImageRequest req, ImageConfig.ImageModeConfig cfg) {
        this.prompt = req.getPrompt();
        this.aspectRatio = req.getAspectRatio();
        this.sizePreset = req.getSizePreset();
        this.quality = req.getQuality();
        this.imageCount = req.getImageCount() > 0 ? req.getImageCount() : 1;
        this.seed = req.getSeed() > 0 ? req.getSeed() : null;

        validate(cfg);
    }

    /** 工厂方法：从请求和模型配置构建参数，校验不通过抛 {@link BusinessException}。 */
    public static GeminiGenerateParams of(ImageRequest req, ImageConfig config) {
        if (config == null || !config.supportsGenerate()) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "该模型不支持文生图");
        }
        return new GeminiGenerateParams(req, config.generate());
    }

    private void validate(ImageConfig.ImageModeConfig cfg) {
        if (prompt == null || prompt.isBlank()) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "prompt 不能为空");
        }
        if (cfg.maxImages() != null && imageCount > cfg.maxImages()) {
            throw new BusinessException(
                    GlobalErrorCode.BAD_REQUEST, "imageCount 超出上限 " + cfg.maxImages());
        }
        if (quality != null && cfg.quality() != null && !cfg.quality().contains(quality)) {
            throw new BusinessException(
                    GlobalErrorCode.BAD_REQUEST,
                    "不支持的 quality: " + quality + "，可选: " + cfg.quality());
        }
        if (sizePreset != null
                && cfg.sizePresets() != null
                && !cfg.sizePresets().contains(sizePreset)) {
            throw new BusinessException(
                    GlobalErrorCode.BAD_REQUEST,
                    "不支持的 sizePreset: " + sizePreset + "，可选: " + cfg.sizePresets());
        }
    }

    /** 生成 Gemini generateContent 请求 body。 */
    public Map<String, Object> toBody() {
        var textPart = Map.of("text", prompt);
        var userContent = Map.of("role", "user", "parts", List.of(textPart));

        var genConfig = new LinkedHashMap<String, Object>();
        genConfig.put("responseModalities", List.of("IMAGE"));
        if (imageCount > 1) genConfig.put("candidateCount", imageCount);
        if (seed != null) genConfig.put("seed", seed);

        var imageFormat = new LinkedHashMap<String, Object>();
        if (aspectRatio != null) imageFormat.put("aspectRatio", aspectRatio);
        if (sizePreset != null) imageFormat.put("imageSize", sizePreset);
        if (!imageFormat.isEmpty()) {
            genConfig.put("imageConfig", imageFormat);
        }

        var body = new LinkedHashMap<String, Object>();
        body.put("contents", List.of(userContent));
        body.put("generationConfig", genConfig);
        return body;
    }
}
