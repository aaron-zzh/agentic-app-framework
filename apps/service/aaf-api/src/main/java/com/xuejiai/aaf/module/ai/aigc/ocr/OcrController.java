package com.xuejiai.aaf.module.ai.aigc.ocr;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.intelligent.ai.ocr.OcrService;
import com.xuejiai.aaf.framework.intelligent.ai.ocr.vo.OcrRequest;
import com.xuejiai.aaf.framework.intelligent.ai.ocr.vo.OcrResult;
import com.xuejiai.aaf.framework.intelligent.ai.ocr.vo.OcrTask;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRouter;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRoutingContext;
import com.xuejiai.aaf.framework.intelligent.core.registry.AiServiceRegistry;
import com.xuejiai.aaf.framework.security.OperatorContext;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import com.xuejiai.aaf.framework.security.license.FeatureRequired;
import com.xuejiai.aaf.framework.security.license.LicenseFeature;

/**
 * OCR 文字识别接口。
 *
 * <p>积分预检与结算由 {@link com.xuejiai.aaf.framework.engine.credit.AiCreditAspect} 切面统一处理。
 */
@FeatureRequired(LicenseFeature.Codes.AIGC)
@Tag(name = "OCR 文字识别")
@RestController
@RequestMapping("/api/ai/ocr")
@RequiredArgsConstructor
public class OcrController {

    private final AiServiceRegistry aiServiceRegistry;
    private final CapabilityRouter capabilityRouter;
    private final OperatorContext operatorContext;

    @Operation(summary = "OCR 识别")
    @PostMapping("/recognize")
    public Result<OcrResult> recognize(@Valid @RequestBody OcrRecognizeDTO dto) {
        Long userId = operatorContext.currentOwnerId().orElse(null);

        // 走模型决策链
        var ctx =
                CapabilityRoutingContext.of(
                        userId, CapabilityRoutingContext.CAP_OCR, dto.modelId());
        var model = capabilityRouter.resolve(ctx);

        OcrRequest request =
                new OcrRequest(
                        dto.imageUrl(),
                        dto.imageBase64(),
                        dto.prompt(),
                        dto.task() != null ? OcrTask.valueOf(dto.task()) : null,
                        dto.resultSchema(),
                        dto.enableRotate() != null && dto.enableRotate(),
                        dto.minPixels() != null ? dto.minPixels() : OcrRequest.DEFAULT_MIN_PIXELS,
                        dto.maxPixels() != null ? dto.maxPixels() : OcrRequest.DEFAULT_MAX_PIXELS,
                        model.getModelId(),
                        dto.imageWidth(),
                        dto.imageHeight());

        // 积分预检 + 结算由 OcrServiceDecorator 装饰器统一处理
        return Result.success(
                aiServiceRegistry.get(OcrService.class, model).recognize(model, request));
    }

    @Operation(summary = "OCR 流式识别（SSE）")
    @PostMapping(value = "/recognize/stream", produces = "text/event-stream")
    public SseEmitter streamRecognize(@Valid @RequestBody OcrRecognizeDTO dto) {
        Long userId = operatorContext.currentOwnerId().orElse(null);
        var ctx =
                CapabilityRoutingContext.of(
                        userId, CapabilityRoutingContext.CAP_OCR, dto.modelId());
        var model = capabilityRouter.resolve(ctx);
        OcrRequest request =
                new OcrRequest(
                        dto.imageUrl(),
                        dto.imageBase64(),
                        dto.prompt(),
                        dto.task() != null ? OcrTask.valueOf(dto.task()) : null,
                        dto.resultSchema(),
                        dto.enableRotate() != null && dto.enableRotate(),
                        dto.minPixels() != null ? dto.minPixels() : OcrRequest.DEFAULT_MIN_PIXELS,
                        dto.maxPixels() != null ? dto.maxPixels() : OcrRequest.DEFAULT_MAX_PIXELS,
                        model.getModelId(),
                        dto.imageWidth(),
                        dto.imageHeight());

        var emitter = new SseEmitter(5 * 60 * 1000L);
        Thread.startVirtualThread(
                () -> {
                    try {
                        aiServiceRegistry
                                .get(OcrService.class, model)
                                .streamRecognize(model, request)
                                .blockingForEach(
                                        chunk ->
                                                emitter.send(
                                                        SseEmitter.event()
                                                                .name("text")
                                                                .data(chunk)));
                        emitter.send(SseEmitter.event().name("done").data(""));
                        emitter.complete();
                    } catch (Exception e) {
                        try {
                            emitter.send(SseEmitter.event().name("error").data(e.getMessage()));
                        } catch (Exception ignored) {
                        }
                        emitter.completeWithError(e);
                    }
                });
        return emitter;
    }

    /**
     * OCR 请求入参。imageUrl 和 imageBase64 二选一。 参数校验由 {@link
     * com.xuejiai.aaf.framework.intelligent.ai.ocr.vo.OcrRequest#validate()} 统一执行。
     *
     * @param imageUrl 图像公网 URL，支持 BMP/JPEG/PNG/TIFF/WEBP/HEIC 格式
     * @param imageBase64 Base64 编码图像，格式：data:image/jpeg;base64,...，编码后 ≤ 10MB
     * @param task 内置任务类型（TEXT_RECOGNITION/KEY_INFORMATION_EXTRACTION/TABLE_PARSING/
     *     DOCUMENT_PARSING/FORMULA_RECOGNITION/ADVANCED_RECOGNITION/MULTI_LAN）
     * @param prompt 自定义提示词；task 和 prompt 都为空时使用模型默认
     * @param resultSchema 信息抽取字段模板 JSON（task=KEY_INFORMATION_EXTRACTION 时有效）
     * @param enableRotate 是否开启图像自动转正，默认 false
     * @param minPixels 图像最小像素阈值，默认 3072
     * @param maxPixels 图像最大像素阈值，默认 8388608
     * @param modelId 期望模型 ID（可选，走决策链）
     */
    public record OcrRecognizeDTO(
            String imageUrl,
            String imageBase64,
            String task,
            String prompt,
            String resultSchema,
            Boolean enableRotate,
            Integer minPixels,
            Integer maxPixels,
            String modelId,
            Integer imageWidth,
            Integer imageHeight) {}
}
