package com.xuejiai.aaf.module.ai.aigc.image.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.intelligent.ai.image.ImageGenerationService;
import com.xuejiai.aaf.framework.intelligent.ai.image.vo.ImageEditRequest;
import com.xuejiai.aaf.framework.intelligent.ai.image.vo.ImageResult;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRouter;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRoutingContext;
import com.xuejiai.aaf.framework.intelligent.core.registry.AiServiceRegistry;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.framework.security.license.FeatureRequired;
import com.xuejiai.aaf.framework.security.license.LicenseFeature;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;

/**
 * 图像编辑接口（图生图 / 局部编辑）。
 *
 * <p>文生图和 Midjourney 任务统一走 {@code /api/aigc/tasks} AIGC 任务接口提交。
 *
 * @author AaronZZH & Kiro
 */
@FeatureRequired(LicenseFeature.Codes.AIGC)
@Tag(name = "图像生成")
@RestController
@RequestMapping("/api/system/images")
@RequiredArgsConstructor
public class ImageController {

    private final AiServiceRegistry aiServiceRegistry;
    private final CapabilityRouter capabilityRouter;
    private final OperatorContext operatorContext;

    // ========== 请求 DTO ==========

    /** 图生图请求 DTO。 */
    public record ImageToImageRequest(
            @NotBlank String sourceUrl,
            @NotBlank String prompt,
            @NotBlank String modelId,
            Double strength) {}

    /** 局部编辑请求 DTO。 */
    public record ImageEditDTO(
            @NotBlank String sourceUrl,
            String maskUrl,
            @NotBlank String prompt,
            @NotBlank String modelId) {}

    // ========== 图生图 / 局部编辑 ==========

    @Operation(summary = "图生图（参考图 + 风格 Prompt + 强度）")
    @PostMapping("/image-to-image")
    public Result<ImageResult> imageToImage(@RequestBody @Valid ImageToImageRequest request) {
        Long userId = operatorContext.currentOwnerId().orElse(null);
        var model =
                capabilityRouter.resolve(
                        CapabilityRoutingContext.of(
                                userId, CapabilityRoutingContext.CAP_IMAGE_GEN, request.modelId()));
        var editRequest =
                new ImageEditRequest(
                        request.sourceUrl(),
                        null,
                        request.prompt(),
                        request.strength() != null ? request.strength() : 0.75,
                        model.getModelId());
        var result =
                aiServiceRegistry
                        .get(ImageGenerationService.class, model)
                        .imageToImage(model, editRequest);
        return Result.success(result);
    }

    @Operation(summary = "局部编辑（原图 + 蒙版 + 编辑 Prompt）")
    @PostMapping("/edit")
    public Result<ImageResult> editImage(@RequestBody @Valid ImageEditDTO request) {
        Long userId = operatorContext.currentOwnerId().orElse(null);
        var model =
                capabilityRouter.resolve(
                        CapabilityRoutingContext.of(
                                userId, CapabilityRoutingContext.CAP_IMAGE_GEN, request.modelId()));
        var editRequest =
                new ImageEditRequest(
                        request.sourceUrl(),
                        request.maskUrl(),
                        request.prompt(),
                        null,
                        model.getModelId());
        var result =
                aiServiceRegistry
                        .get(ImageGenerationService.class, model)
                        .editImage(model, editRequest);
        return Result.success(result);
    }
}
