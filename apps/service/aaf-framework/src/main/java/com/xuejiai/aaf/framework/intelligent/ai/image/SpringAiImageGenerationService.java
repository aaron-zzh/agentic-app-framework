package com.xuejiai.aaf.framework.intelligent.ai.image;

import java.util.List;

import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.openai.OpenAiImageOptions;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.xuejiai.aaf.framework.intelligent.ai.chat.AiProperties;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModelRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 基于动态构建的 OpenAI 兼容 ImageModel 的文生图实现，支持多供应商（OpenAI / N1N 等）。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpringAiImageGenerationService implements ImageGenerationService {

    private final DynamicImageModelFactory imageModelFactory;
    private final AiModelRepository modelRepository;
    private final AiProperties aiProperties;

    @Override
    public ImageResult generate(ImageRequest request) {
        var aiModel = modelRepository.findByModelIdAndEnabledTrue(request.modelId()).orElse(null);
        // GPT image 模型直接走 HTTP（传 background/output_format/moderation 等参数）
        if (aiModel != null && isGptImageModel(aiModel.getModelName())) {
            return generateViaHttp(request, aiModel);
        }
        // 其他模型走 Spring AI
        var imageModel = imageModelFactory.get(request.modelId());
        String sizeStr =
                (request.width() != null && request.height() != null)
                        ? request.width() + "x" + request.height()
                        : null;
        var optBuilder = OpenAiImageOptions.builder().responseFormat("url");
        if (sizeStr != null) optBuilder.size(sizeStr);
        if (request.quality() != null) optBuilder.quality(request.quality());
        if (request.count() > 1) optBuilder.N(request.count());
        var response = imageModel.call(new ImagePrompt(request.prompt(), optBuilder.build()));
        List<String> urls =
                response.getResults().stream()
                        .map(r -> r.getOutput())
                        .map(o -> o.getUrl() != null ? o.getUrl() : o.getB64Json())
                        .filter(s -> s != null && !s.isBlank())
                        .toList();
        return ImageResult.ofUrls(urls, request.modelId());
    }

    /** GPT image 模型：直接 HTTP POST /images/generations（JSON body，支持全部参数） */
    private ImageResult generateViaHttp(
            ImageRequest request,
            com.xuejiai.aaf.framework.intelligent.core.model.AiModel aiModel) {
        String apiKey = resolveApiKey(aiModel);
        String baseUrl = aiModel.effectiveBaseUrl().replaceAll("/$", "");
        String modelName = aiModel.getModelName();
        try {
            var body = new java.util.LinkedHashMap<String, Object>();
            body.put("model", modelName);
            body.put("prompt", request.prompt());
            if (request.width() != null && request.height() != null)
                body.put("size", request.width() + "x" + request.height());
            if (request.quality() != null) body.put("quality", request.quality());
            if (request.format() != null) body.put("output_format", request.format());
            if (request.count() > 1) body.put("n", request.count());
            if (request.background() != null) body.put("background", request.background());
            if (request.moderation() != null) body.put("moderation", request.moderation());

            var response =
                    RestClient.create()
                            .post()
                            .uri(baseUrl + "/images/generations")
                            .header("Authorization", "Bearer " + apiKey)
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .body(body)
                            .retrieve()
                            .body(String.class);

            String b64 = null, url = null;
            List<String> urls = new java.util.ArrayList<>();
            var responseNode = parseJson(response);
            if (responseNode != null && responseNode.has("data")) {
                for (var item : responseNode.get("data")) {
                    String b = item.path("b64_json").asText(null);
                    String u = item.path("url").asText(null);
                    if (b != null && !b.isBlank()) urls.add(b);
                    else if (u != null && !u.isBlank()) urls.add(u);
                    if (b64 == null && b != null && !b.isBlank()) b64 = b;
                    if (url == null && u != null && !u.isBlank()) url = u;
                }
            }
            log.info("文生图完成(HTTP): modelId={}, count={}, url={}, b64={}", request.modelId(), urls.size(), url, b64 != null ? b64.substring(0, Math.min(20, b64.length())) + "..." : null);
            return urls.size() > 1
                    ? ImageResult.ofUrls(urls, request.modelId())
                    : new ImageResult(url, b64, request.modelId());
        } catch (Exception e) {
            log.error("文生图失败(HTTP): modelId={}", request.modelId(), e);
            throw new RuntimeException("图像生成失败: " + e.getMessage(), e);
        }
    }

    private boolean isGptImageModel(String modelName) {
        return modelName != null
                && (modelName.startsWith("gpt-image")
                        || modelName.startsWith("dall-e")
                        || modelName.startsWith("doubao-seedream"));
    }

    /** 图像编辑：调用 /v1/images/edits（JSON body，images 数组传 image_url）。 */
    @Override
    public ImageResult imageToImage(ImageEditRequest req) {
        String modelId = req.model();
        var aiModel = modelRepository.findByModelIdAndEnabledTrue(modelId).orElseThrow();
        String apiKey = resolveApiKey(aiModel);
        String baseUrl = aiModel.effectiveBaseUrl().replaceAll("/$", "");
        String modelName = aiModel.getModelName();

        try {
            var body = new java.util.LinkedHashMap<String, Object>();
            body.put("model", modelName);
            body.put("prompt", req.prompt());
            // images 数组：每张图用 image_url 传递（支持最多16张）
            var images = new java.util.ArrayList<java.util.Map<String, String>>();
            for (String imgUrl : req.allSourceUrls()) {
                images.add(java.util.Map.of("image_url", imgUrl));
            }
            body.put("images", images);
            if (req.maskUrl() != null)
                body.put("mask", java.util.Map.of("image_url", req.maskUrl()));
            if (req.quality() != null) body.put("quality", req.quality());
            if (req.format() != null) body.put("output_format", req.format());
            if (req.background() != null) body.put("background", req.background());
            if (req.contentModeration() != null) body.put("moderation", req.contentModeration());
            if (req.n() != null && req.n() > 1) body.put("n", req.n());

            var response =
                    RestClient.create()
                            .post()
                            .uri(baseUrl + "/images/edits")
                            .header("Authorization", "Bearer " + apiKey)
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .body(body)
                            .retrieve()
                            .body(String.class);

            String b64 = null, url = null;
            List<String> urls = new java.util.ArrayList<>();
            var responseNode = parseJson(response);
            if (responseNode != null && responseNode.has("data")) {
                for (var item : responseNode.get("data")) {
                    String b = item.path("b64_json").asText(null);
                    String u = item.path("url").asText(null);
                    if (b != null && !b.isBlank()) urls.add(b);
                    else if (u != null && !u.isBlank()) urls.add(u);
                    if (b64 == null && b != null && !b.isBlank()) b64 = b;
                    if (url == null && u != null && !u.isBlank()) url = u;
                }
            }
            log.info("[SpringAiImage] 图像编辑完成: modelId={}, count={}", modelId, urls.size());
            return urls.size() > 1
                    ? ImageResult.ofUrls(urls, modelId)
                    : new ImageResult(url, b64, modelId);
        } catch (Exception e) {
            log.error("[SpringAiImage] 图像编辑失败: modelId={}", modelId, e);
            throw new RuntimeException("图像编辑失败: " + e.getMessage(), e);
        }
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

    private com.fasterxml.jackson.databind.JsonNode parseJson(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
        } catch (Exception e) {
            log.warn("[SpringAiImage] JSON 解析失败: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public ImageResult editImage(ImageEditRequest request) {
        return imageToImage(request);
    }
}
