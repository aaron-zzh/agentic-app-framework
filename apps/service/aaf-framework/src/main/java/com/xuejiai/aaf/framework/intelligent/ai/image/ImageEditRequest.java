package com.xuejiai.aaf.framework.intelligent.ai.image;

/**
 * 图片编辑请求（图生图 / 局部编辑通用）。
 *
 * @param sourceUrl 原图 URL
 * @param maskUrl 蒙版区域 URL（局部编辑时使用，图生图时为 null）
 * @param prompt 编辑/风格提示词
 * @param strength 变换强度（0.0~1.0，越大越偏离原图）
 * @param model 模型 ID（如 dall-e-2）
 */
public record ImageEditRequest(
        String sourceUrl, String maskUrl, String prompt, Double strength, String model) {

    public ImageEditRequest(String sourceUrl, String prompt) {
        this(sourceUrl, null, prompt, 0.75, null);
    }
}
