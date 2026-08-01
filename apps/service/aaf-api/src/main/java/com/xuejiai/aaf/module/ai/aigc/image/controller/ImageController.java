package com.xuejiai.aaf.module.ai.aigc.image.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.enums.pay.CreditTransactionCategoryEnum;
import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.engine.credit.AiCreditGuard;
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
@PreAuthorize("isAuthenticated()")
public class ImageController {

    private final AiServiceRegistry aiServiceRegistry;
    private final CapabilityRouter capabilityRouter;
    private final OperatorContext operatorContext;
    private final AiCreditGuard creditGuard;

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
        Long userId = requireOwnerId();
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
        var service = aiServiceRegistry.get(ImageGenerationService.class, model);
        // M23：接回统一权益 precheck——原实现直接调用底层服务，跳过了余额预检；
        // AiServiceRegistry 返回的实例已被 ImageGenServiceDecorator 包裹，调用成功后会自动结算，
        // 但结算前若未预检，透支/欠费账户仍可发起真实调用。estimateCost 与统一任务链
        // （AigcTaskService#submitImageTask）用的是同一套默认估算逻辑。
        var estimatedCost =
                service.estimateCost(model, editRequest, creditGuard.getMarkupRate());
        creditGuard.precheck(userId, CreditTransactionCategoryEnum.IMAGE_GEN.getCode(), estimatedCost);
        var result = service.imageToImage(model, editRequest);
        return Result.success(result);
    }

    @Operation(summary = "局部编辑（原图 + 蒙版 + 编辑 Prompt）")
    @PostMapping("/edit")
    public Result<ImageResult> editImage(@RequestBody @Valid ImageEditDTO request) {
        Long userId = requireOwnerId();
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
        var service = aiServiceRegistry.get(ImageGenerationService.class, model);
        // M23：同上，接回统一权益 precheck。
        var estimatedCost =
                service.estimateCost(model, editRequest, creditGuard.getMarkupRate());
        creditGuard.precheck(userId, CreditTransactionCategoryEnum.IMAGE_GEN.getCode(), estimatedCost);
        var result = service.editImage(model, editRequest);
        return Result.success(result);
    }

    /** M23：precheck 需要确定归属账户才能预检余额，不允许匿名/未解析身份直接调用付费能力。 */
    private Long requireOwnerId() {
        return operatorContext
                .currentOwnerId()
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.UNAUTHORIZED, "未登录"));
    }
}
