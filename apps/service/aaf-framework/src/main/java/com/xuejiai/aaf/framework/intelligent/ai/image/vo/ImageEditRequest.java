package com.xuejiai.aaf.framework.intelligent.ai.image.vo;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 图片编辑请求（图生图 / 局部编辑通用），继承 ImageRequest 复用生成参数。
 *
 * <p>编辑专有字段：sourceUrl / maskUrl / strength / sourceUrls
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ImageEditRequest extends ImageRequest {

    /** 原图 URL */
    private String sourceUrl;

    /** 蒙版区域 URL（局部编辑时使用，图生图时为 null） */
    private String maskUrl;

    /** 变换强度（0.0~1.0，越大越偏离原图） */
    private Double strength = 0.75;

    /** 多参考图 URL 列表（优先于 sourceUrl） */
    private List<String> sourceUrls;

    public ImageEditRequest(String sourceUrl, String prompt) {
        setSourceUrl(sourceUrl);
        setPrompt(prompt);
    }

    public ImageEditRequest(
            String sourceUrl, String maskUrl, String prompt, Double strength, String modelId) {
        setSourceUrl(sourceUrl);
        setMaskUrl(maskUrl);
        setPrompt(prompt);
        setStrength(strength != null ? strength : 0.75);
        setModelId(modelId);
    }

    public ImageEditRequest(
            String sourceUrl,
            String maskUrl,
            String prompt,
            Double strength,
            String modelId,
            String quality,
            String format,
            String background,
            String contentModeration,
            Integer n,
            List<String> sourceUrls) {
        setSourceUrl(sourceUrl);
        setMaskUrl(maskUrl);
        setPrompt(prompt);
        setStrength(strength != null ? strength : 0.75);
        setModelId(modelId);
        setQuality(quality);
        setFormat(format);
        setBackground(background);
        setModeration(contentModeration);
        if (n != null) setImageCount(n);
        setSourceUrls(sourceUrls);
    }

    /** 获取所有参考图 URL（sourceUrls 优先，否则用单个 sourceUrl） */
    public List<String> allSourceUrls() {
        if (sourceUrls != null && !sourceUrls.isEmpty()) return sourceUrls;
        if (sourceUrl != null) return List.of(sourceUrl);
        return List.of();
    }
}
