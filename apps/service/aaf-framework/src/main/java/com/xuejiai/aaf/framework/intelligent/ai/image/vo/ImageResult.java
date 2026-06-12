package com.xuejiai.aaf.framework.intelligent.ai.image.vo;

import java.util.List;

/**
 * 图片生成结果。
 *
 * @param url 图片 URL（responseFormat=url 时有值）
 * @param b64Json Base64 编码（responseFormat=b64_json 时有值）
 * @param modelId 实际使用的模型 ID
 * @param urls 多图结果列表
 */
public record ImageResult(String url, String b64Json, String modelId, List<String> urls) {

    /** 兼容旧调用：单图结果 */
    public ImageResult(String url, String b64Json, String modelId) {
        this(url, b64Json, modelId, url != null ? List.of(url) : List.of());
    }

    /** 多图结果，urls 非空时 url 取第一张 */
    public static ImageResult ofUrls(List<String> urls, String modelId) {
        String first = (urls != null && !urls.isEmpty()) ? urls.get(0) : null;
        return new ImageResult(first, null, modelId, urls != null ? urls : List.of());
    }
}
