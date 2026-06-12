package com.xuejiai.aaf.module.examples.image.controller;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.intelligent.ai.image.ImageGenerationService;
import com.xuejiai.aaf.framework.intelligent.ai.image.ImageProcessService;
import com.xuejiai.aaf.framework.intelligent.ai.image.vo.ImageResult;
import com.xuejiai.aaf.module.examples.image.service.ImageExampleService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 图像能力示例接口。
 *
 * <p>调用封装好的 {@link ImageGenerationService} 和 {@link ImageProcessService}，不直接使用 SDK。
 *
 * <p>启用条件：{@code aaf.examples.image.enabled=true}
 */
@Tag(name = "示例 - 图像能力")
@RestController
@RequestMapping("/api/examples/image")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "aaf.examples.image.enabled", havingValue = "true")
public class ImageExampleController {

    private final ImageExampleService imageExampleService;

    @Operation(summary = "文生图（DALL-E / wanx）")
    @PostMapping("/generate")
    public Result<ImageResult> generate(
            @Valid @RequestBody ImageExampleService.GenerateRequest req) {
        return Result.success(imageExampleService.generate(req));
    }

    @Operation(summary = "图像处理（阿里云百炼：色彩增强 / 卡通化）")
    @PostMapping("/process")
    public Result<ImageProcessService.ProcessResult> process(
            @Valid @RequestBody ImageExampleService.ProcessRequest req) {
        return Result.success(imageExampleService.process(req));
    }

    @Operation(summary = "查询异步图像处理任务结果（卡通化等）")
    @GetMapping("/process/{taskId}")
    public Result<ImageProcessService.ProcessResult> queryTask(@PathVariable String taskId) {
        return Result.success(imageExampleService.queryTask(taskId));
    }
}
