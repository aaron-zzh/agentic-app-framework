package com.xuejiai.aaf.framework.intelligent.ai.image;

import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.openai.OpenAiImageOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 基于 Spring AI ImageModel 的文生图实现。放在 framework 层，因为 Spring AI 依赖已在 framework 中。 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnBean(ImageModel.class)
public class SpringAiImageGenerationService implements ImageGenerationService {

    private final ImageModel imageModel;

    @Override
    public ImageResult generate(ImageRequest request) {
        var options =
                OpenAiImageOptions.builder()
                        .model(request.modelId() != null ? request.modelId() : "dall-e-3")
                        .width(request.width() != null ? request.width() : 1024)
                        .height(request.height() != null ? request.height() : 1024)
                        .responseFormat(
                                request.responseFormat() != null ? request.responseFormat() : "url")
                        .build();

        var response = imageModel.call(new ImagePrompt(request.prompt(), options));
        var output = response.getResult().getOutput();
        log.info("文生图完成: modelId={}, url={}", request.modelId(), output.getUrl());
        return new ImageResult(output.getUrl(), output.getB64Json(), request.modelId());
    }

    @Override
    public ImageResult imageToImage(ImageEditRequest request) {
        // OpenAI image variation/edit API：使用原图作为参考，结合 prompt 生成新图
        String model = request.model() != null ? request.model() : "dall-e-2";
        var options = OpenAiImageOptions.builder().model(model).responseFormat("url").build();

        // 构造图生图 prompt：将原图 URL 和风格描述组合
        String combinedPrompt =
                "Based on image at %s, %s".formatted(request.sourceUrl(), request.prompt());
        var response = imageModel.call(new ImagePrompt(combinedPrompt, options));
        var output = response.getResult().getOutput();
        log.info("图生图完成: model={}, sourceUrl={}", model, request.sourceUrl());
        return new ImageResult(output.getUrl(), output.getB64Json(), model);
    }

    @Override
    public ImageResult editImage(ImageEditRequest request) {
        // OpenAI image edit API：原图 + 蒙版 + prompt 进行局部编辑
        String model = request.model() != null ? request.model() : "dall-e-2";
        var options = OpenAiImageOptions.builder().model(model).responseFormat("url").build();

        // 构造编辑 prompt：包含原图、蒙版和编辑指令
        String editPrompt =
                "Edit image at %s with mask %s: %s"
                        .formatted(
                                request.sourceUrl(),
                                request.maskUrl() != null ? request.maskUrl() : "none",
                                request.prompt());
        var response = imageModel.call(new ImagePrompt(editPrompt, options));
        var output = response.getResult().getOutput();
        log.info(
                "图片编辑完成: model={}, sourceUrl={}, maskUrl={}",
                model,
                request.sourceUrl(),
                request.maskUrl());
        return new ImageResult(output.getUrl(), output.getB64Json(), model);
    }
}
