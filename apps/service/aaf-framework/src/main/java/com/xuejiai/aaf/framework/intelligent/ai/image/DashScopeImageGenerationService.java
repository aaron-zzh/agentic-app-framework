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
import com.alibaba.dashscope.aigc.imagegeneration.ImageGenerationOutput;
import com.alibaba.dashscope.aigc.imagegeneration.ImageGenerationParam;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesis;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesisParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationOutput;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.common.MultiModalMessage;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.utils.Constants;

import com.xuejiai.aaf.framework.intelligent.ai.image.vo.ImageEditRequest;
import com.xuejiai.aaf.framework.intelligent.ai.image.vo.ImageRequest;
import com.xuejiai.aaf.framework.intelligent.ai.image.vo.ImageResult;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModel;

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
@Service("dashScopeImageGenerationService")
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
    public ImageResult generate(AiModel model, ImageRequest req) {
        String modelStr = stripNamespace(req.getModelId());
        log.info("[DashScopeImage] 开始生成: model={}", modelStr);

        if (modelStr.startsWith("wan2.")) {
            // wan2: size 优先用 sizePreset 档位（"1K"/"2K"/"4K"），否则用像素字符串
            String size = req.resolveSize();
            return callWan2(
                    modelStr, req.getPrompt(), size, req.getSeed(), req.getImageCount(), null);
        }
        if (modelStr.startsWith("qwen-image-2")) {
            String size =
                    req.getWidth() > 0 && req.getHeight() > 0
                            ? req.getWidth() + "*" + req.getHeight()
                            : null;
            return callQwenImage2(
                    modelStr,
                    req.getPrompt(),
                    null,
                    size,
                    req.getNegativePrompt(),
                    req.getSeed(),
                    req.getPromptExtend(),
                    req.getImageCount());
        }
        String size = req.getWidth() + "*" + req.getHeight();
        return callImageSynthesis(
                modelStr,
                req.getPrompt(),
                size,
                req.getNegativePrompt(),
                req.getSeed(),
                req.getImageCount());
    }

    @Override
    public ImageResult imageToImage(AiModel model, ImageEditRequest req) {
        String modelStr =
                req.getModelId() != null ? stripNamespace(req.getModelId()) : "qwen-image-2.0-pro";
        var urls = req.allSourceUrls().isEmpty() ? null : req.allSourceUrls();
        return generateWithImages(
                modelStr,
                req.getPrompt(),
                urls,
                req.getEditSize(),
                req.getSeed(),
                req.getImageCount() > 1 ? req.getImageCount() : 1,
                req.getSizePreset() != null);
    }

    @Override
    public ImageResult editImage(AiModel model, ImageEditRequest req) {
        return imageToImage(model, req);
    }

    /** {@link #generateWithImages} 的 ImageRequest 重载。 */
    public ImageResult generateWithImages(ImageRequest req) {
        return generateWithImages(
                req.getModelId(),
                req.getPrompt(),
                req.getImageUrls(),
                req.getEditSize(),
                req.getSeed(),
                req.getImageCount(),
                req.getSizePreset() != null);
    }

    /** 供 AigcTaskExecutor 调用，携带 AiModel 用于装饰器积分结算。 */
    public ImageResult generateWithImages(AiModel model, ImageRequest req) {
        return generateWithImages(req);
    }

    public ImageResult generateWithImages(
            String modelId,
            String prompt,
            List<String> imageUrls,
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
            List<String> imageUrls) {
        try {
            var contentList = new ArrayList<Map<String, Object>>();
            // 引用内容已由调用方准备为 Data URL 或短时签名 URL。
            if (imageUrls != null) {
                for (String imageUrl : imageUrls) {
                    contentList.add(Collections.singletonMap("image", imageUrl));
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
            List<String> imageUrls,
            String size,
            String negativePrompt,
            Integer seed,
            Boolean promptExtend,
            int count) {
        try {
            List<Map<String, Object>> contentList = new ArrayList<>();
            // 引用内容已由调用方准备为 Data URL 或短时签名 URL。
            if (imageUrls != null) {
                for (String imageUrl : imageUrls) {
                    contentList.add(Collections.singletonMap("image", imageUrl));
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

    private String extractUrlFromChoices(List<ImageGenerationOutput.Choice> choices) {
        List<String> urls = extractAllUrlsFromChoices(choices);
        return urls.isEmpty() ? null : urls.get(0);
    }

    private List<String> extractAllUrlsFromChoices(List<ImageGenerationOutput.Choice> choices) {
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

    private String extractUrlFromMultiModal(List<MultiModalConversationOutput.Choice> choices) {
        List<String> urls = extractAllUrlsFromMultiModal(choices);
        return urls.isEmpty() ? null : urls.get(0);
    }

    private List<String> extractAllUrlsFromMultiModal(
            List<MultiModalConversationOutput.Choice> choices) {
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
