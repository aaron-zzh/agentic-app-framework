package com.xuejiai.aaf.module.ai.aigc.image.controller;

import java.util.List;

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
import com.xuejiai.aaf.module.system.file.api.FileStoragePort;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;

/**
 * 图像编辑接口（局部编辑）。
 *
 * <p>文生图、图生图（多参考图）和 Midjourney 任务统一走 {@code /api/aigc/tasks} AIGC 任务接口提交； 局部编辑（原图 +
 * 蒙版）尚未纳入统一任务链，保留本接口作为独立入口。
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
    private final FileStoragePort fileService;

    // ========== 请求 DTO ==========

    /**
     * 局部编辑请求 DTO。
     *
     * <p>图像以 fileId 传递并校验当前用户所有权，不接受裸 URL——避免本地存储场景下把受权限保护的 {@code /api/system/files/{id}/content}
     * 直接交给外部 AI 服务下载导致 401。
     */
    public record ImageEditDTO(
            @NotNull Long sourceFileId,
            Long maskFileId,
            @NotBlank String prompt,
            @NotBlank String modelId) {}

    // ========== 局部编辑 ==========

    @Operation(summary = "局部编辑（原图 + 蒙版 + 编辑 Prompt）")
    @PostMapping("/edit")
    public Result<ImageResult> editImage(@RequestBody @Valid ImageEditDTO request) {
        Long userId = requireOwnerId();
        fileService.requireCurrentOwner(request.sourceFileId());
        if (request.maskFileId() != null) {
            fileService.requireCurrentOwner(request.maskFileId());
        }
        var model =
                capabilityRouter.resolve(
                        CapabilityRoutingContext.of(
                                userId, CapabilityRoutingContext.CAP_IMAGE_GEN, request.modelId()));
        // 按存储类型分流解析图像数据（本地→data URL，OSS→签名 URL），避免裸 URL 直传外部服务 401。
        String sourceData = fileService.resolveImageData(List.of(request.sourceFileId())).get(0);
        String maskData =
                request.maskFileId() != null
                        ? fileService.resolveImageData(List.of(request.maskFileId())).get(0)
                        : null;
        var editRequest =
                new ImageEditRequest(
                        sourceData, maskData, request.prompt(), null, model.getModelId());
        var service = aiServiceRegistry.get(ImageGenerationService.class, model);
        // M23：同上，接回统一权益 precheck。
        var estimatedCost = service.estimateCost(model, editRequest, creditGuard.getMarkupRate());
        creditGuard.precheck(
                userId, CreditTransactionCategoryEnum.IMAGE_GEN.getCode(), estimatedCost);
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
