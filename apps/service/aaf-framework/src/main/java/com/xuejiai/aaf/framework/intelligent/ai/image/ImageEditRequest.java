package com.xuejiai.aaf.framework.intelligent.ai.image;

/**
 * 图片编辑请求（图生图 / 局部编辑通用）。
 *
 * @param sourceUrl 原图 URL
 * @param maskUrl 蒙版区域 URL（局部编辑时使用，图生图时为 null）
 * @param prompt 编辑/风格提示词
 * @param strength 变换强度（0.0~1.0，越大越偏离原图）
 * @param model 模型 ID（如 gpt-image-2）
 * @param quality 画质：low / medium / high / auto
 * @param format 图片格式：png / jpeg / webp
 * @param background 背景模式：auto / transparent / opaque
 * @param contentModeration 内容审核级别：auto / low
 * @param n 生成张数
 */
public record ImageEditRequest(
        String sourceUrl,
        String maskUrl,
        String prompt,
        Double strength,
        String model,
        String quality,
        String format,
        String background,
        String contentModeration,
        Integer n,
        java.util.List<String> sourceUrls) {

    public ImageEditRequest(String sourceUrl, String prompt) {
        this(sourceUrl, null, prompt, 0.75, null, null, null, null, null, null, null);
    }

    public ImageEditRequest(
            String sourceUrl, String maskUrl, String prompt, Double strength, String model) {
        this(sourceUrl, maskUrl, prompt, strength, model, null, null, null, null, null, null);
    }

    /** 获取所有参考图 URL（sourceUrls 优先，否则用单个 sourceUrl） */
    public java.util.List<String> allSourceUrls() {
        if (sourceUrls != null && !sourceUrls.isEmpty()) return sourceUrls;
        if (sourceUrl != null) return java.util.List.of(sourceUrl);
        return java.util.List.of();
    }
}
