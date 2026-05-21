package com.xuejiai.aaf.framework.intelligent.ai.image;

import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.openai.OpenAiImageOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.intelligent.ai.image.ImageGenerationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 基于 Spring AI ImageModel 的文生图实现。放在 framework 层，因为 Spring AI 依赖已在 framework 中。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnBean(ImageModel.class)
public class SpringAiImageGenerationService implements ImageGenerationService {

    private final ImageModel imageModel;

    @Override
    public ImageResult generate(ImageRequest request) {
        var options = OpenAiImageOptions.builder()
                .model(request.modelId() != null ? request.modelId() : "dall-e-3")
                .width(request.width() != null ? request.width() : 1024)
                .height(request.height() != null ? request.height() : 1024)
                .responseFormat(request.responseFormat() != null ? request.responseFormat() : "url")
                .build();

        var response = imageModel.call(new ImagePrompt(request.prompt(), options));
        var output = response.getResult().getOutput();
        log.info("文生图完成: modelId={}, url={}", request.modelId(), output.getUrl());
        return new ImageResult(output.getUrl(), output.getB64Json(), request.modelId());
    }
}
