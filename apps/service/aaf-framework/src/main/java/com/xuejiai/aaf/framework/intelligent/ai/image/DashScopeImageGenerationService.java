package com.xuejiai.aaf.framework.intelligent.ai.image;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.alibaba.dashscope.aigc.imagegeneration.ImageGeneration;
import com.alibaba.dashscope.aigc.imagegeneration.ImageGenerationMessage;
import com.alibaba.dashscope.aigc.imagegeneration.ImageGenerationParam;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesis;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesisParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.common.MultiModalMessage;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.utils.Constants;

import lombok.extern.slf4j.Slf4j;

/**
 * 百炼（DashScope）统一图像生成服务——同步调用，三分支内部路由。
 *
 * <p>路由规则（按 modelName 前缀）：
 *
 * <ul>
 *   <li>{@code wan2.} → {@link ImageGeneration}（wan2.x 系列，messages 风格）
 *   <li>{@code qwen-image-2} → {@link MultiModalConversation}（qwen-image-2.0/pro，支持编辑）
 *   <li>其余 → {@link ImageSynthesis}（qwen-image-max/plus 等旧版）
 * </ul>
 *
 * <p>所有分支均同步调用，由上层 {@code @Async} 包装为非阻塞任务。 不支持的参数字段在对应分支中静默忽略。
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "spring.ai.dashscope.api-key", matchIfMissing = false)
public class DashScopeImageGenerationService implements ImageGenerationService {

    static {
        Constants.baseHttpApiUrl = "https://dashscope.aliyuncs.com/api/v1";
    }

    private final String apiKey;
    private final ImageGeneration imageGeneration = new ImageGeneration();
    private final ImageSynthesis imageSynthesis = new ImageSynthesis();
    private final MultiModalConversation multiModalConv = new MultiModalConversation();

    public DashScopeImageGenerationService(
            @Value("${spring.ai.dashscope.api-key:}") String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public ImageResult generate(ImageRequest req) {
        String model = stripNamespace(req.modelId());
        log.info("[DashScopeImage] 生成: model={}", model);

        if (model.startsWith("wan2.")) {
            // wan2: size 优先用 sizePreset 档位（"1K"/"2K"/"4K"），否则用像素字符串
            String size =
                    req.sizePreset() != null
                            ? req.sizePreset()
                            : (req.width() != null && req.height() != null
                                    ? req.width() + "*" + req.height()
                                    : null);
            return callWan2(model, req.prompt(), size, req.seed(), req.count(), null);
        }
        if (model.startsWith("qwen-image-2")) {
            String size =
                    req.width() != null && req.height() != null
                            ? req.width() + "*" + req.height()
                            : null;
            return callQwenImage2(
                    model,
                    req.prompt(),
                    null,
                    size,
                    req.negativePrompt(),
                    req.seed(),
                    req.promptExtend(),
                    req.count());
        }
        String size = req.width() + "*" + req.height();
        return callImageSynthesis(
                model, req.prompt(), size, req.negativePrompt(), req.seed(), req.count());
    }

    @Override
    public ImageResult imageToImage(ImageEditRequest req) {
        String model = req.model() != null ? stripNamespace(req.model()) : "qwen-image-2.0-pro";
        var urls = req.sourceUrl() != null ? java.util.List.of(req.sourceUrl()) : null;
        return callQwenImage2(model, req.prompt(), urls, null, null, null, null, 1);
    }

    @Override
    public ImageResult editImage(ImageEditRequest req) {
        return imageToImage(req);
    }

    /**
     * 带多张参考图的生成/编辑（供 AigcTaskExecutor 直接调用）。
     *
     * @param isPresetSize true 表示 size 是档位字符串（1K/2K），false 表示像素字符串
     */
    public ImageResult generateWithImages(
            String modelId,
            String prompt,
            java.util.List<String> imageUrls,
            String size,
            Integer seed,
            int count,
            boolean isPresetSize) {
        String model = stripNamespace(modelId);
        if (model.startsWith("wan2.")) {
            return callWan2(model, prompt, size, seed, count, imageUrls);
        }
        // qwen-image-2.x 及其他
        return callQwenImage2(model, prompt, imageUrls, size, null, seed, null, count);
    }

    // ========== 三分支实现 ==========

    /** wan2.x：ImageGeneration，messages 风格，同步，支持档位尺寸/多图编辑 */
    private ImageResult callWan2(
            String model,
            String prompt,
            String size,
            Integer seed,
            int count,
            java.util.List<String> imageUrls) {
        try {
            var contentList = new java.util.ArrayList<java.util.Map<String, Object>>();
            // 有图时先加图片（图像编辑）
            if (imageUrls != null) {
                for (String imgUrl : imageUrls) {
                    contentList.add(Collections.singletonMap("image", imgUrl));
                }
            }
            contentList.add(Collections.singletonMap("text", prompt));

            var message =
                    ImageGenerationMessage.builder().role("user").content(contentList).build();

            var paramBuilder =
                    ImageGenerationParam.builder()
                            .apiKey(apiKey)
                            .model(model)
                            .messages(Collections.singletonList(message))
                            .n(count > 0 ? count : 1);
            if (size != null) paramBuilder.size(size);
            if (seed != null && seed > 0) paramBuilder.seed(seed);

            var result = imageGeneration.call(paramBuilder.build());
            List<String> urls = extractAllUrlsFromChoices(result.getOutput().getChoices());
            log.info("[DashScopeImage][wan2] 完成: model={}, count={}", model, urls.size());
            return ImageResult.ofUrls(urls, model);
        } catch (Exception e) {
            log.error("[DashScopeImage][wan2] 失败: model={}", model, e);
            throw new RuntimeException("wan2 图像生成失败: " + e.getMessage(), e);
        }
    }

    /** qwen-image-2.x：MultiModalConversation，支持编辑，同步 */
    private ImageResult callQwenImage2(
            String model,
            String prompt,
            java.util.List<String> imageUrls,
            String size,
            String negativePrompt,
            Integer seed,
            Boolean promptExtend,
            int count) {
        try {
            List<Map<String, Object>> contentList = new ArrayList<>();
            // 每张参考图下载转 base64
            if (imageUrls != null) {
                for (String url : imageUrls) {
                    contentList.add(Collections.singletonMap("image", toBase64DataUrl(url)));
                }
            }
            contentList.add(Collections.singletonMap("text", prompt));

            var userMessage =
                    MultiModalMessage.builder()
                            .role(Role.USER.getValue())
                            .content(contentList)
                            .build();

            Map<String, Object> parameters = new HashMap<>();
            parameters.put("watermark", false);
            if (size != null) parameters.put("size", size);
            if (negativePrompt != null) parameters.put("negative_prompt", negativePrompt);
            if (seed != null && seed > 0) parameters.put("seed", seed);
            if (promptExtend != null) parameters.put("prompt_extend", promptExtend);
            if (count > 1) parameters.put("n", count);

            var param =
                    MultiModalConversationParam.builder()
                            .apiKey(apiKey)
                            .model(model)
                            .messages(Collections.singletonList(userMessage))
                            .parameters(parameters)
                            .build();

            var result = multiModalConv.call(param);
            List<String> urls = extractAllUrlsFromMultiModal(result.getOutput().getChoices());
            log.info("[DashScopeImage][qwen2] 完成: model={}, count={}", model, urls.size());
            return ImageResult.ofUrls(urls, model);
        } catch (Exception e) {
            log.error("[DashScopeImage][qwen2] 失败: model={}", model, e);
            throw new RuntimeException("qwen-image-2 图像生成失败: " + e.getMessage(), e);
        }
    }

    /** 将图片 URL 下载并转为 data:{mime};base64,{data} 格式 */
    private String toBase64DataUrl(String imageUrl) {
        try (var is = java.net.URI.create(imageUrl).toURL().openStream()) {
            byte[] bytes = is.readAllBytes();
            String mime = "image/png"; // 默认 png，OSS URL 通常不带扩展名
            if (imageUrl.contains(".jpg") || imageUrl.contains(".jpeg")) mime = "image/jpeg";
            else if (imageUrl.contains(".webp")) mime = "image/webp";
            return "data:"
                    + mime
                    + ";base64,"
                    + java.util.Base64.getEncoder().encodeToString(bytes);
        } catch (Exception e) {
            log.warn("[DashScopeImage] 图片下载失败，回退使用 URL: {}", imageUrl);
            return imageUrl; // 下载失败时降级用 URL（部分情况 DashScope 也接受 URL）
        }
    }

    /** qwen-image-max/plus 等旧版：ImageSynthesis，同步 call */
    private ImageResult callImageSynthesis(
            String model,
            String prompt,
            String size,
            String negativePrompt,
            Integer seed,
            int count) {
        try {
            var builder =
                    ImageSynthesisParam.builder()
                            .apiKey(apiKey)
                            .model(model)
                            .prompt(prompt)
                            .n(count > 0 ? count : 1)
                            .size(size);
            if (negativePrompt != null) builder.negativePrompt(negativePrompt);
            if (seed != null && seed > 0) builder.seed(seed);

            var result = imageSynthesis.call(builder.build());
            var results = result.getOutput().getResults();
            List<String> urls = new ArrayList<>();
            if (results != null) {
                for (var r : results) {
                    String u = r.get("url");
                    if (u != null) urls.add(u);
                }
            }
            log.info("[DashScopeImage][synthesis] 完成: model={}, count={}", model, urls.size());
            return ImageResult.ofUrls(urls, model);
        } catch (Exception e) {
            log.error("[DashScopeImage][synthesis] 失败: model={}", model, e);
            throw new RuntimeException("图像生成失败: " + e.getMessage(), e);
        }
    }

    // ========== 工具方法 ==========

    private String stripNamespace(String modelId) {
        if (modelId == null) return "";
        return modelId.contains(":") ? modelId.substring(modelId.indexOf(':') + 1) : modelId;
    }

    private String extractUrlFromChoices(
            List<com.alibaba.dashscope.aigc.imagegeneration.ImageGenerationOutput.Choice> choices) {
        List<String> urls = extractAllUrlsFromChoices(choices);
        return urls.isEmpty() ? null : urls.get(0);
    }

    private List<String> extractAllUrlsFromChoices(
            List<com.alibaba.dashscope.aigc.imagegeneration.ImageGenerationOutput.Choice> choices) {
        List<String> urls = new ArrayList<>();
        if (choices == null) return urls;
        for (var choice : choices) {
            for (var item : choice.getMessage().getContent()) {
                Object img = item.get("image");
                if (img != null) {
                    urls.add(String.valueOf(img));
                    continue;
                }
                Object url = item.get("url");
                if (url != null) urls.add(String.valueOf(url));
            }
        }
        return urls;
    }

    private String extractUrlFromMultiModal(
            List<
                            com.alibaba.dashscope.aigc.multimodalconversation
                                    .MultiModalConversationOutput.Choice>
                    choices) {
        List<String> urls = extractAllUrlsFromMultiModal(choices);
        return urls.isEmpty() ? null : urls.get(0);
    }

    private List<String> extractAllUrlsFromMultiModal(
            List<
                            com.alibaba.dashscope.aigc.multimodalconversation
                                    .MultiModalConversationOutput.Choice>
                    choices) {
        List<String> urls = new ArrayList<>();
        if (choices == null) return urls;
        for (var choice : choices) {
            for (var item : choice.getMessage().getContent()) {
                Object img = item.get("image");
                if (img != null) urls.add(String.valueOf(img));
            }
        }
        return urls;
    }
}
