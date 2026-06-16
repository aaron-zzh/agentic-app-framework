package com.xuejiai.aaf.framework.intelligent.ai.image.vo;

import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;

import lombok.extern.slf4j.Slf4j;

/**
 * Gemini 图像编辑参数——从 {@link ImageEditRequest} + {@link ImageConfig} 构建，构造时完成校验。
 *
 * <p>{@link #toBody()} 调用时下载参考图并转为 base64 inline_data 内联到请求体。
 */
@Slf4j
public final class GeminiEditParams {

    private final String prompt;
    private final List<String> sourceUrls;
    private final String quality;
    private final String aspectRatio;
    private final String sizePreset;

    private GeminiEditParams(ImageEditRequest req, ImageConfig.ImageModeConfig cfg) {
        this.prompt = req.getPrompt();
        this.sourceUrls = req.allSourceUrls();
        this.quality = req.getQuality();
        this.aspectRatio = req.getAspectRatio();
        this.sizePreset = req.getSizePreset();

        validate(cfg);
    }

    /** 工厂方法：从请求和模型配置构建参数，校验不通过抛 {@link BusinessException}。 */
    public static GeminiEditParams of(ImageEditRequest req, ImageConfig config) {
        if (config == null || !config.supportsEdit()) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "该模型不支持图像编辑");
        }
        return new GeminiEditParams(req, config.edit());
    }

    private void validate(ImageConfig.ImageModeConfig cfg) {
        if (prompt == null || prompt.isBlank()) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "prompt 不能为空");
        }
        if (sourceUrls.isEmpty()) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "图像编辑至少需要一张参考图");
        }
        if (cfg.maxInputImages() != null && sourceUrls.size() > cfg.maxInputImages()) {
            throw new BusinessException(
                    GlobalErrorCode.BAD_REQUEST, "参考图数量超出上限 " + cfg.maxInputImages());
        }
        if (quality != null && cfg.quality() != null && !cfg.quality().contains(quality)) {
            throw new BusinessException(
                    GlobalErrorCode.BAD_REQUEST,
                    "不支持的 quality: " + quality + "，可选: " + cfg.quality());
        }
    }

    /** 生成 Gemini generateContent 请求 body，下载参考图转为 base64 inline_data。 */
    public Map<String, Object> toBody() {
        var parts = new ArrayList<Map<String, Object>>();

        // prompt 在前，参考图在后——Gemini 推荐顺序，有助于模型理解参考图用途
        parts.add(Map.of("text", prompt));
        for (String url : sourceUrls) {
            try {
                byte[] bytes = URI.create(url).toURL().openStream().readAllBytes();
                String b64 = Base64.getEncoder().encodeToString(bytes);
                String mime = guessMime(url);
                parts.add(Map.of("inline_data", Map.of("mime_type", mime, "data", b64)));
            } catch (Exception e) {
                log.warn("[GeminiEditParams] 参考图下载失败，跳过: url={}", url, e);
            }
        }

        var userContent = Map.of("role", "user", "parts", parts);
        var genConfig = new LinkedHashMap<String, Object>();
        genConfig.put("responseModalities", List.of("IMAGE"));

        var imageFormat = new LinkedHashMap<String, Object>();
        if (aspectRatio != null) imageFormat.put("aspectRatio", aspectRatio);
        if (sizePreset != null) imageFormat.put("imageSize", sizePreset);
        if (!imageFormat.isEmpty()) genConfig.put("imageConfig", imageFormat);

        var body = new LinkedHashMap<String, Object>();
        body.put("contents", List.of(userContent));
        body.put("generationConfig", genConfig);
        return body;
    }

    private static String guessMime(String url) {
        String lower = url.split("\\?")[0].toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".gif")) return "image/gif";
        return "image/png";
    }
}
