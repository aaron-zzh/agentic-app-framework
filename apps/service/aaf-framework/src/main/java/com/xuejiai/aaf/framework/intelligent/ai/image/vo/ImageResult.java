package com.xuejiai.aaf.framework.intelligent.ai.image.vo;

import java.util.List;

/**
 * 图片生成结果。
 *
 * @param url 图片 URL（responseFormat=url 时有值）
 * @param b64Json Base64 编码（responseFormat=b64_json 时有值）
 * @param modelId 实际使用的模型 ID
 * @param urls 多图结果列表
 * @param inputTokens 输入 token 数（按 token 计费模型有值）
 * @param outputTokens 输出 token 数（按 token 计费模型有值）
 */
public record ImageResult(
        String url,
        String b64Json,
        String modelId,
        List<String> urls,
        int inputTokens,
        int outputTokens) {

    /** 兼容旧调用：单图结果，无 token 信息 */
    public ImageResult(String url, String b64Json, String modelId) {
        this(url, b64Json, modelId, url != null ? List.of(url) : List.of(), 0, 0);
    }

    /** 兼容旧调用：多图结果，无 token 信息 */
    public ImageResult(String url, String b64Json, String modelId, List<String> urls) {
        this(url, b64Json, modelId, urls, 0, 0);
    }

    /** 多图结果，urls 非空时 url 取第一张 */
    public static ImageResult ofUrls(List<String> urls, String modelId) {
        String first = (urls != null && !urls.isEmpty()) ? urls.get(0) : null;
        return new ImageResult(first, null, modelId, urls != null ? urls : List.of(), 0, 0);
    }

    /** 带 token 用量的多图结果 */
    public static ImageResult ofUrls(
            List<String> urls, String modelId, int inputTokens, int outputTokens) {
        String first = (urls != null && !urls.isEmpty()) ? urls.get(0) : null;
        return new ImageResult(
                first, null, modelId, urls != null ? urls : List.of(), inputTokens, outputTokens);
    }
}
