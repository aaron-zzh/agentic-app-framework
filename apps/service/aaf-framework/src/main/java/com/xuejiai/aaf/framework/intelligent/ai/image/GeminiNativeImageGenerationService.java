package com.xuejiai.aaf.framework.intelligent.ai.image;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.xuejiai.aaf.framework.intelligent.ai.image.vo.GeminiEditParams;
import com.xuejiai.aaf.framework.intelligent.ai.image.vo.GeminiGenerateParams;
import com.xuejiai.aaf.framework.intelligent.ai.image.vo.ImageEditRequest;
import com.xuejiai.aaf.framework.intelligent.ai.image.vo.ImageRequest;
import com.xuejiai.aaf.framework.intelligent.ai.image.vo.ImageResult;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModel;
import com.xuejiai.aaf.framework.intelligent.core.model.ModelManagementService;

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

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ModelManagementService modelManagementService;

    @Override
    public ImageResult generate(ImageRequest request) {
        var aiModel = modelManagementService.getModel(request.getModelId());
        var config = modelManagementService.resolveImageConfig(request.getModelId());
        var params = GeminiGenerateParams.of(request, config);
        log.debug(
                "[GeminiNative] generate 提交: modelId={}, prompt={}, aspectRatio={}, sizePreset={}",
                request.getModelId(),
                request.getPrompt(),
                request.getAspectRatio(),
                request.getSizePreset());
        return call(aiModel, params.toBody(), "generate", request.getModelId());
    }

    @Override
    public ImageResult imageToImage(ImageEditRequest request) {
        var aiModel = modelManagementService.getModel(request.getModelId());
        var config = modelManagementService.resolveImageConfig(request.getModelId());
        var params = GeminiEditParams.of(request, config);
        log.debug(
                "[GeminiNative] edit 提交: modelId={}, prompt={}, sourceUrls={}",
                request.getModelId(),
                request.getPrompt(),
                request.allSourceUrls().size());
        return call(aiModel, params.toBody(), "edit", request.getModelId());
    }

    @Override
    public ImageResult editImage(ImageEditRequest request) {
        return imageToImage(request);
    }

    // ========== 内部方法 ==========

    /** 递归 redact 请求体用于日志：把 inline_data 的 base64 {@code data} 字段替换为长度占位， 避免把整段图片 base64 打进日志。 */
    @SuppressWarnings("unchecked")
    private static Object redactForLog(Object node) {
        if (node instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                Object value = entry.getValue();
                if ("data".equals(key) && value instanceof String s) {
                    copy.put(key, "<base64 " + s.length() + " chars>");
                } else {
                    copy.put(key, redactForLog(value));
                }
            }
            return copy;
        }
        if (node instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            for (Object item : list) {
                copy.add(redactForLog(item));
            }
            return copy;
        }
        return node;
    }

    private ImageResult call(AiModel aiModel, Map<String, Object> body, String op, String modelId) {
        String apiKey = modelManagementService.resolveApiKey(modelId);
        String baseUrl = aiModel.effectiveBaseUrl().replaceAll("/$", "");
        String modelName = aiModel.getModelName();
        try {
            // 请求体含 base64 内联图片（inline_data.data），打印前 redact 掉，避免刷爆日志/泄露数据
            if (log.isDebugEnabled()) {
                log.debug("[GeminiNative] {} body: {}", op, redactForLog(body));
            }
            var response =
                    RestClient.builder()
                            .requestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory() {{
                                setConnectTimeout(10_000);
                                setReadTimeout(0); // 不限读超时，等待 Gemini 返回
                            }})
                            .build()
                            .post()
                            .uri(buildGeminiUrl(baseUrl, modelName))
                            .header("x-goog-api-key", apiKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(body)
                            .retrieve()
                            .body(String.class);

            String b64 = extractB64(response);
            if (b64 == null) {
                throw new RuntimeException("Gemini 未返回图片，请检查日志中的完整响应");
            }
            log.info("[GeminiNative] {} 完成: modelId={}", op, modelId);
            return new ImageResult(null, b64, modelId);
        } catch (Exception e) {
            log.error("[GeminiNative] {} 失败: modelId={}", op, modelId, e);
            throw new RuntimeException("Gemini 图像" + op + "失败: " + e.getMessage(), e);
        }
    }

    private String extractB64(String responseBody) {
        try {
            var node = MAPPER.readTree(responseBody);
            var parts = node.path("candidates").get(0).path("content").path("parts");
            for (var part : parts) {
                var inlineData = part.path("inlineData");
                if (inlineData.isMissingNode()) inlineData = part.path("inline_data");
                if (!inlineData.isMissingNode()) {
                    String data = inlineData.path("data").asText(null);
                    if (data != null && !data.isBlank()) return data;
                }
            }
        } catch (Exception e) {
            log.warn("[GeminiNative] extractB64 解析失败", e);
        }
        return null;
    }

    /** 从 baseUrl 提取根域名，拼接 /v1beta/models/{model}:generateContent */
    private String buildGeminiUrl(String baseUrl, String modelName) {
        String root = baseUrl.replaceAll("/(v\\d+beta?|v\\d+)/?$", "");
        return root + "/v1beta/models/" + modelName + ":generateContent";
    }
}
