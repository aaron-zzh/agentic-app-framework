package com.xuejiai.aaf.module.ai.aigc.image.service;

import java.util.List;
import java.util.function.Function;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import com.xuejiai.aaf.framework.intelligent.ai.image.ImageServiceFactory;
import com.xuejiai.aaf.framework.intelligent.ai.image.vo.ImageRequest;
import com.xuejiai.aaf.module.ai.aigc.media.service.MediaAssetService;
import com.xuejiai.aaf.module.ai.aigc.media.vo.SaveFromGenerationDTO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 图像生成工具 — 注册为 Spring AI Function，可被对话中的 AI 调用。
 *
 * @author AaronZZH & Kiro
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class ImageGenerationTool {

    private final ImageServiceFactory imageServiceFactory;
    private final MediaAssetService mediaAssetService;

    /** 工具输入参数。 */
    public record Request(String prompt, String model, String size, List<Long> referenceAssetIds) {}

    /** 工具输出结果。 */
    public record Response(String imageUrl, String message) {}

    @Bean
    @Description("根据用户描述生成图片。参数：prompt(必填)-图片描述, model(可选)-模型名称, size(可选)-尺寸如1024x1024")
    public Function<Request, Response> generateImage() {
        return request -> {
            log.info("对话触发图像生成: prompt={}, model={}", request.prompt(), request.model());
            try {
                var width = 1024;
                var height = 1024;
                if (request.size() != null && request.size().contains("x")) {
                    var parts = request.size().split("x");
                    width = Integer.parseInt(parts[0]);
                    height = Integer.parseInt(parts[1]);
                }

                var model = request.model() != null ? request.model() : "dall-e-3";
                var service = imageServiceFactory.getSyncService(model);
                var result =
                        service.generate(
                                new ImageRequest(request.prompt(), model, width, height, "url"));

                // 自动保存到素材库
                try {
                    var saveDto =
                            new SaveFromGenerationDTO(
                                    null,
                                    null,
                                    result.url(),
                                    null,
                                    "{\"prompt\":\"%s\",\"model\":\"%s\"}"
                                            .formatted(
                                                    request.prompt().replace("\"", "\\\""), model),
                                    width,
                                    height,
                                    null,
                                    null,
                                    null,
                                    true,
                                    null,
                                    null,
                                    null);
                    // 工具调用场景无用户上下文，使用系统用户 ID 0
                    mediaAssetService.saveFromGeneration(0L, saveDto);
                } catch (Exception e) {
                    log.warn("自动保存素材库失败: {}", e.getMessage());
                }

                return new Response(result.url(), "图片已生成");
            } catch (Exception e) {
                log.error("对话生成图片失败: {}", e.getMessage(), e);
                return new Response(null, "生成失败: " + e.getMessage());
            }
        };
    }
}
