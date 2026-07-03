package com.xuejiai.aaf.framework.intelligent.ai.image;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.openai.OpenAiImageOptions;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.intelligent.ai.chat.AiProperties;
import com.xuejiai.aaf.framework.intelligent.ai.image.vo.ImageEditRequest;
import com.xuejiai.aaf.framework.intelligent.ai.image.vo.ImageRequest;
import com.xuejiai.aaf.framework.intelligent.ai.image.vo.ImageResult;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModel;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModelRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;

/** 基于动态构建的 OpenAI 兼容 ImageModel 的文生图实现，支持多供应商（OpenAI / N1N 等）。 */
@Slf4j
@Service("springAiImageGenerationService")
@RequiredArgsConstructor
public class SpringAiImageGenerationService implements ImageGenerationService {

    private final DynamicImageModelFactory imageModelFactory;
    private final AiModelRepository modelRepository;
    private final AiProperties aiProperties;

    @Override
    public ImageResult generate(AiModel model, ImageRequest request) {
        var aiModel =
                modelRepository.findByModelIdAndEnabledTrue(request.getModelId()).orElse(null);
        // GPT image 模型直接走 HTTP（传 background/output_format/moderation 等参数）
        if (aiModel != null && isGptImageModel(aiModel.getModelName())) {
            return generateViaHttp(request, aiModel);
        }
        // 其他模型走 Spring AI
        var imageModel = imageModelFactory.get(request.getModelId());
        String sizeStr =
                (!request.isAutoSize() && request.getWidth() > 0 && request.getHeight() > 0)
                        ? request.getWidth() + "x" + request.getHeight()
                        : null;
        var optBuilder = OpenAiImageOptions.builder().responseFormat("url");
        if (sizeStr != null) optBuilder.size(sizeStr);
        if (request.getQuality() != null) optBuilder.quality(request.getQuality());
        if (request.getImageCount() > 1) optBuilder.N(request.getImageCount());
        var response = imageModel.call(new ImagePrompt(request.getPrompt(), optBuilder.build()));
        List<String> urls =
                response.getResults().stream()
                        .map(r -> r.getOutput())
                        .map(o -> o.getUrl() != null ? o.getUrl() : o.getB64Json())
                        .filter(s -> s != null && !s.isBlank())
                        .toList();
        return ImageResult.ofUrls(urls, request.getModelId());
    }

    /** GPT image 模型：直接 HTTP POST /images/generations（JSON body，支持全部参数） */
    private ImageResult generateViaHttp(ImageRequest request, AiModel aiModel) {
        String apiKey = resolveApiKey(aiModel);
        String baseUrl = aiModel.effectiveBaseUrl().replaceAll("/$", "");
        String modelName = aiModel.getModelName();
        try {
            var body = new LinkedHashMap<String, Object>();
            body.put("model", modelName);
            body.put("prompt", request.getPrompt());
            if (!request.isAutoSize() && request.getWidth() > 0 && request.getHeight() > 0) {
                // 对宽高取整为 16 的倍数，校验宽高比在 1:3 ~ 3:1 之间
                int w = (request.getWidth() / 16) * 16;
                int h = (request.getHeight() / 16) * 16;
                double ratio = (double) w / h;
                if (w > 0 && h > 0 && ratio >= 1.0 / 3 && ratio <= 3.0) {
                    body.put("size", w + "x" + h);
                } else {
                    log.warn(
                            "[SpringAiImage] 无效尺寸 {}x{}，回退到 1024x1024",
                            request.getWidth(),
                            request.getHeight());
                    body.put("size", "1024x1024");
                }
            }
            if (request.getQuality() != null) body.put("quality", request.getQuality());
            if (request.getFormat() != null) body.put("output_format", request.getFormat());
            if (request.getImageCount() > 1) body.put("n", request.getImageCount());
            if (request.getBackground() != null) body.put("background", request.getBackground());
            if (request.getModeration() != null) body.put("moderation", request.getModeration());

            log.debug(
                    "[SpringAiImage] 文生图请求: model={}, size={}, quality={}, format={}, n={}",
                    modelName,
                    body.get("size"),
                    body.get("quality"),
                    body.get("output_format"),
                    body.getOrDefault("n", 1));

            // gpt-image-2 文生图走 /images/generations（官方接口规范）
            var response =
                    RestClient.builder()
                            .requestFactory(
                                    new HttpComponentsClientHttpRequestFactory(
                                            HttpClients.custom()
                                                    .setDefaultRequestConfig(
                                                            RequestConfig.custom()
                                                                    .setResponseTimeout(
                                                                            120_000,
                                                                            TimeUnit.MILLISECONDS)
                                                                    .build())
                                                    .build()))
                            .build()
                            .post()
                            .uri(baseUrl + "/images/generations")
                            .header("Authorization", "Bearer " + apiKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(body)
                            .retrieve()
                            .body(String.class);

            String b64 = null, url = null;
            List<String> urls = new ArrayList<>();
            var responseNode = parseJson(response);
            if (responseNode != null && responseNode.has("data")) {
                for (var item : responseNode.get("data")) {
                    String b = item.path("b64_json").asString(null);
                    String u = item.path("url").asString(null);
                    if (b != null && !b.isBlank()) urls.add(b);
                    else if (u != null && !u.isBlank()) urls.add(u);
                    if (b64 == null && b != null && !b.isBlank()) b64 = b;
                    if (url == null && u != null && !u.isBlank()) url = u;
                }
            }
            log.info(
                    "文生图完成(HTTP): modelId={}, count={}, url={}, b64={}",
                    request.getModelId(),
                    urls.size(),
                    url,
                    b64 != null ? b64.substring(0, Math.min(20, b64.length())) + "..." : null);
            int inputTokens = 0, outputTokens = 0;
            if (responseNode != null && responseNode.has("usage")) {
                var usage = responseNode.get("usage");
                inputTokens = usage.path("prompt_tokens").asInt(0);
                outputTokens = usage.path("completion_tokens").asInt(0);
            }
            return urls.size() > 1
                    ? ImageResult.ofUrls(urls, request.getModelId(), inputTokens, outputTokens)
                    : new ImageResult(
                            url,
                            b64,
                            request.getModelId(),
                            url != null ? List.of(url) : List.of(),
                            inputTokens,
                            outputTokens);
        } catch (Exception e) {
            log.error("文生图失败(HTTP): modelId={}", request.getModelId(), e);
            throw new RuntimeException("图像生成失败: " + friendlyMessage(e), e);
        }
    }

    private boolean isGptImageModel(String modelName) {
        return modelName != null
                && (modelName.startsWith("gpt-image")
                        || modelName.startsWith("dall-e")
                        || modelName.startsWith("doubao-seedream"));
    }

    /** 图像编辑：调用 /v1/images/edits（multipart/form-data，OpenAI 标准格式）。 */
    @Override
    public ImageResult imageToImage(AiModel model, ImageEditRequest req) {
        String modelId = req.getModelId();
        var aiModel = modelRepository.findByModelIdAndEnabledTrue(modelId).orElseThrow();
        String apiKey = resolveApiKey(aiModel);
        String baseUrl = aiModel.effectiveBaseUrl().replaceAll("/$", "");
        String modelName = aiModel.getModelName();

        try {
            var multipart = new MultipartBodyBuilder();
            multipart.part("model", modelName);
            multipart.part("prompt", req.getPrompt());

            // 下载图片并以 binary 形式上传
            List<String> srcUrls = req.allSourceUrls();
            for (int i = 0; i < srcUrls.size(); i++) {
                byte[] bytes =
                        java.net.URI.create(srcUrls.get(i)).toURL().openStream().readAllBytes();
                String srcUrl = srcUrls.get(i).split("\\?")[0].toLowerCase();
                String ext =
                        srcUrl.endsWith(".webp")
                                ? "webp"
                                : srcUrl.endsWith(".jpg") || srcUrl.endsWith(".jpeg")
                                        ? "jpg"
                                        : "png";
                String mime =
                        ext.equals("webp")
                                ? "image/webp"
                                : ext.equals("jpg") ? "image/jpeg" : "image/png";
                String fname = "image_" + i + "." + ext;
                String fieldName = srcUrls.size() == 1 ? "image" : "image[]";
                var mediaType = MediaType.parseMediaType(mime);
                multipart.part(
                        fieldName,
                        new ByteArrayResource(bytes) {
                            @Override
                            public String getFilename() {
                                return fname;
                            }
                        },
                        mediaType);
            }
            if (req.getMaskUrl() != null) {
                byte[] maskBytes =
                        java.net.URI.create(req.getMaskUrl()).toURL().openStream().readAllBytes();
                multipart.part(
                        "mask",
                        new ByteArrayResource(maskBytes) {
                            @Override
                            public String getFilename() {
                                return "mask.png";
                            }
                        },
                        MediaType.IMAGE_PNG);
            }
            if (req.getQuality() != null) multipart.part("quality", req.getQuality());
            if (req.getFormat() != null) multipart.part("output_format", req.getFormat());
            if (req.getBackground() != null) multipart.part("background", req.getBackground());
            if (req.getImageCount() > 1) multipart.part("n", String.valueOf(req.getImageCount()));
            // gpt-image-2 edits 接口 size 格式为 WxH
            String editSize = req.getEditSize();
            if (editSize != null) multipart.part("size", editSize.replace("*", "x"));

            var response =
                    RestClient.builder()
                            .requestFactory(
                                    new HttpComponentsClientHttpRequestFactory(
                                            HttpClients.custom()
                                                    .setDefaultRequestConfig(
                                                            RequestConfig.custom()
                                                                    .setResponseTimeout(
                                                                            120_000,
                                                                            TimeUnit.MILLISECONDS)
                                                                    .build())
                                                    .build()))
                            .build()
                            .post()
                            .uri(baseUrl + "/images/edits")
                            .header("Authorization", "Bearer " + apiKey)
                            .body(multipart.build())
                            .retrieve()
                            .body(String.class);

            String b64 = null, url = null;
            List<String> urls = new ArrayList<>();
            var responseNode = parseJson(response);
            if (responseNode != null && responseNode.has("data")) {
                for (var item : responseNode.get("data")) {
                    String b = item.path("b64_json").asString(null);
                    String u = item.path("url").asString(null);
                    if (b != null && !b.isBlank()) urls.add(b);
                    else if (u != null && !u.isBlank()) urls.add(u);
                    if (b64 == null && b != null && !b.isBlank()) b64 = b;
                    if (url == null && u != null && !u.isBlank()) url = u;
                }
            }
            log.info("[SpringAiImage] 图像编辑完成: modelId={}, count={}", modelId, urls.size());
            int inputTokens = 0, outputTokens = 0;
            if (responseNode != null && responseNode.has("usage")) {
                var usage = responseNode.get("usage");
                inputTokens = usage.path("prompt_tokens").asInt(0);
                outputTokens = usage.path("completion_tokens").asInt(0);
            }
            return urls.size() > 1
                    ? ImageResult.ofUrls(urls, modelId, inputTokens, outputTokens)
                    : new ImageResult(
                            url,
                            b64,
                            modelId,
                            url != null ? List.of(url) : List.of(),
                            inputTokens,
                            outputTokens);
        } catch (Exception e) {
            log.error("[SpringAiImage] 图像编辑失败: modelId={}", modelId, e);
            throw new RuntimeException("图像编辑失败: " + friendlyMessage(e), e);
        }
    }

    private static String friendlyMessage(Exception e) {
        Throwable t = e;
        while (t != null) {
            String msg = t.getMessage() != null ? t.getMessage() : "";
            if (t instanceof SocketTimeoutException
                    || msg.contains("timed out")
                    || msg.contains("timeout")
                    || msg.contains("I/O error")) {
                return "请求超时，请稍后重试";
            }
            if (t instanceof ConnectException || msg.contains("Connection refused")) {
                return "无法连接到图像服务，请检查网络";
            }
            if (t instanceof UnknownHostException) {
                return "域名解析失败，请检查网络连接";
            }
            t = t.getCause();
        }
        return e.getMessage();
    }

    private String resolveApiKey(AiModel model) {
        String apiKey = model.effectiveApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            var models = aiProperties.getModels();
            var cfg = models.getOrDefault(model.getProvider(), models.get("default"));
            if (cfg != null) apiKey = cfg.getApiKey();
        }
        return apiKey != null ? apiKey : "";
    }

    private JsonNode parseJson(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return JsonUtils.readTree(json);
        } catch (Exception e) {
            log.warn("[SpringAiImage] JSON 解析失败: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public ImageResult editImage(AiModel model, ImageEditRequest request) {
        return imageToImage(model, request);
    }
}
