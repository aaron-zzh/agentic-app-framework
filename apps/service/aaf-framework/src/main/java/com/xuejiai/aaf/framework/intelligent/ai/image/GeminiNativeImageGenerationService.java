package com.xuejiai.aaf.framework.intelligent.ai.image;

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
        log.debug("[GeminiNative] generate 提交: modelId={}, prompt={}, aspectRatio={}, sizePreset={}",
                request.getModelId(), request.getPrompt(), request.getAspectRatio(), request.getSizePreset());
        return call(aiModel, params.toBody(), "generate", request.getModelId());
    }

    @Override
    public ImageResult imageToImage(ImageEditRequest request) {
        var aiModel = modelManagementService.getModel(request.getModelId());
        var config = modelManagementService.resolveImageConfig(request.getModelId());
        var params = GeminiEditParams.of(request, config);
        log.debug("[GeminiNative] edit 提交: modelId={}, prompt={}, sourceUrls={}",
                request.getModelId(), request.getPrompt(), request.allSourceUrls().size());
        return call(aiModel, params.toBody(), "edit", request.getModelId());
    }

    @Override
    public ImageResult editImage(ImageEditRequest request) {
        return imageToImage(request);
    }

    // ========== 内部方法 ==========

    private ImageResult call(AiModel aiModel, Map<String, Object> body, String op, String modelId) {
        String apiKey = modelManagementService.resolveApiKey(modelId);
        String baseUrl = aiModel.effectiveBaseUrl().replaceAll("/$", "");
        String modelName = aiModel.getModelName();
        try {
            log.debug("[GeminiNative] {} body: {}", op, body);
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
