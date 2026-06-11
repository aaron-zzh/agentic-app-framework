package com.xuejiai.aaf.framework.intelligent.ai.image;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.xuejiai.aaf.framework.intelligent.ai.chat.AiProperties;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModelRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Gemini 原生图像生成服务。
 *
 * <p>直接调用 Google Gemini 原生接口（/{baseUrl}/v1beta/models/{model}:generateContent）， 支持 aspectRatio /
 * imageSize 等参数，走 OpenAI 兼容层（/v1/chat/completions）无法透传这些参数。
 *
 * <p>路由到本服务的条件：模型 capabilities 含 IMAGE_GEN 且 provider = n1n（Gemini 系列）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiNativeImageGenerationService implements ImageGenerationService {

    private final AiModelRepository modelRepository;
    private final AiProperties aiProperties;

    @Override
    public ImageResult generate(ImageRequest request) {
        var aiModel = modelRepository.findByModelIdAndEnabledTrue(request.modelId()).orElseThrow();
        String apiKey = resolveApiKey(aiModel);
        String baseUrl = aiModel.effectiveBaseUrl().replaceAll("/$", "");
        String modelName = aiModel.getModelName();

        try {
            // contents: 纯文本
            var textPart = Map.of("text", request.prompt());
            var userContent = Map.of("role", "user", "parts", List.of(textPart));

            // generationConfig
            var genConfig = new LinkedHashMap<String, Object>();
            genConfig.put("responseModalities", List.of("TEXT", "IMAGE"));
            if (request.aspectRatio() != null || request.sizePreset() != null) {
                var imageOpts = new LinkedHashMap<String, Object>();
                if (request.aspectRatio() != null)
                    imageOpts.put("aspectRatio", request.aspectRatio());
                if (request.sizePreset() != null) imageOpts.put("imageSize", request.sizePreset());
                genConfig.put("responseFormat", Map.of("image", imageOpts));
            }

            var body = new LinkedHashMap<String, Object>();
            body.put("contents", List.of(userContent));
            body.put("generationConfig", genConfig);

            var response =
                    RestClient.create()
                            .post()
                            .uri(buildGeminiUrl(baseUrl, modelName))
                            .header("x-goog-api-key", apiKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(body)
                            .retrieve()
                            .body(String.class);

            String b64 = extractB64(response);
            log.info("[GeminiNative] 生成完成: modelId={}", request.modelId());
            return new ImageResult(null, b64, request.modelId());
        } catch (Exception e) {
            log.error("[GeminiNative] 生成失败: modelId={}", request.modelId(), e);
            throw new RuntimeException("Gemini 图像生成失败: " + e.getMessage(), e);
        }
    }

    @Override
    public ImageResult imageToImage(ImageEditRequest request) {
        var aiModel = modelRepository.findByModelIdAndEnabledTrue(request.model()).orElseThrow();
        String apiKey = resolveApiKey(aiModel);
        String baseUrl = aiModel.effectiveBaseUrl().replaceAll("/$", "");
        String modelName = aiModel.getModelName();

        try {
            var parts = new ArrayList<Map<String, Object>>();
            // 参考图以 base64 inline_data 传入
            for (String imgUrl : request.allSourceUrls()) {
                byte[] bytes = java.net.URI.create(imgUrl).toURL().openStream().readAllBytes();
                String b64 = java.util.Base64.getEncoder().encodeToString(bytes);
                String mime =
                        imgUrl.contains(".jpg") || imgUrl.contains(".jpeg")
                                ? "image/jpeg"
                                : "image/png";
                parts.add(Map.of("inline_data", Map.of("mime_type", mime, "data", b64)));
            }
            parts.add(Map.of("text", request.prompt()));

            var userContent = Map.of("role", "user", "parts", parts);
            var genConfig = new LinkedHashMap<String, Object>();
            genConfig.put("responseModalities", List.of("TEXT", "IMAGE"));

            var body = new LinkedHashMap<String, Object>();
            body.put("contents", List.of(userContent));
            body.put("generationConfig", genConfig);

            var response =
                    RestClient.create()
                            .post()
                            .uri(buildGeminiUrl(baseUrl, modelName))
                            .header("x-goog-api-key", apiKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(body)
                            .retrieve()
                            .body(String.class);

            String b64 = extractB64(response);
            log.info("[GeminiNative] 图像编辑完成: modelId={}", request.model());
            return new ImageResult(null, b64, request.model());
        } catch (Exception e) {
            log.error("[GeminiNative] 图像编辑失败: modelId={}", request.model(), e);
            throw new RuntimeException("Gemini 图像编辑失败: " + e.getMessage(), e);
        }
    }

    @Override
    public ImageResult editImage(ImageEditRequest request) {
        return imageToImage(request);
    }

    private String extractB64(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) return null;
        try {
            var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(responseBody);
            var candidates = node.path("candidates");
            if (candidates.isMissingNode() || candidates.isEmpty()) return null;
            var parts = candidates.get(0).path("content").path("parts");
            for (var part : parts) {
                var inlineData = part.path("inline_data");
                if (!inlineData.isMissingNode()) {
                    return inlineData.path("data").asText(null);
                }
            }
        } catch (Exception e) {
            log.warn("[GeminiNative] extractB64 解析失败", e);
        }
        return null;
    }

    /** 从 baseUrl（如 https://llm-api.net/v1）提取根域名，拼接 /v1beta/models/... */
    private String buildGeminiUrl(String baseUrl, String modelName) {
        // 去掉末尾的 /v1、/v1beta 等版本路径，保留根域名
        String root = baseUrl.replaceAll("/(v\\d+beta?|v\\d+)/?$", "");
        return root + "/v1beta/models/" + modelName + ":generateContent";
    }

    private String resolveApiKey(com.xuejiai.aaf.framework.intelligent.core.model.AiModel model) {
        String apiKey = model.effectiveApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            var models = aiProperties.getModels();
            var cfg = models.getOrDefault(model.getProvider(), models.get("default"));
            if (cfg != null) apiKey = cfg.getApiKey();
        }
        return apiKey != null ? apiKey : "";
    }
}
