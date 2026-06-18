package com.xuejiai.aaf.module.ai.aigc.image.controller;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.intelligent.ai.image.ImageServiceFactory;
import com.xuejiai.aaf.framework.intelligent.ai.image.MidjourneyImageService;
import com.xuejiai.aaf.framework.intelligent.ai.image.MidjourneyImageService.TaskStatus;
import com.xuejiai.aaf.framework.intelligent.ai.image.vo.ImageEditRequest;
import com.xuejiai.aaf.framework.intelligent.ai.image.vo.ImageResult;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRouter;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRoutingContext;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.ai.aigc.image.service.AiImageService;
import com.xuejiai.aaf.module.ai.aigc.image.vo.AiImageVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;

/**
 * 图像生成接口（Midjourney）。
 *
 * <p>依赖 {@link MidjourneyImageService}，需配置 {@code aaf.ai.midjourney.enabled=true}。
 *
 * @author AaronZZH & Kiro
 */
@Tag(name = "图像生成")
@RestController
@RequestMapping("/api/system/images")
@RequiredArgsConstructor
public class ImageController {

    /** Midjourney 服务（可选，需配置 aaf.ai.midjourney.enabled=true） */
    @Autowired(required = false)
    private MidjourneyImageService midjourneyService;

    private final AiImageService aiImageService;
    private final ImageServiceFactory imageServiceFactory;
    private final CapabilityRouter capabilityRouter;
    private final OperatorContext operatorContext;
    private final ObjectMapper objectMapper;

    // ========== 请求 DTO ==========

    public record ImagineRequest(
            @NotBlank String prompt,
            /** 垫图 Base64 列表（可选） */
            List<String> base64Images) {}

    public record ActionRequest(@NotBlank String taskId, @NotBlank String customId) {}

    public record ImageActionRequest(Long imageId, @NotBlank String customId) {}

    public record BatchQueryRequest(@NotEmpty List<String> taskIds) {}

    // ========== 业务端点 ==========

    @Operation(summary = "提交文生图任务（Midjourney imagine）")
    @PostMapping("/midjourney/imagine")
    public Result<Long> imagine(@RequestBody @Valid ImagineRequest request) {
        Long userId = operatorContext.currentUserId().orElseThrow();
        Long id = aiImageService.imagine(userId, request.prompt(), request.base64Images());
        return Result.success(id);
    }

    @Operation(summary = "执行后续操作（放大/变体/重绘）")
    @PostMapping("/midjourney/action")
    public Result<Long> action(@RequestBody @Valid ImageActionRequest request) {
        Long userId = operatorContext.currentUserId().orElseThrow();
        Long id = aiImageService.action(userId, request.imageId(), request.customId());
        return Result.success(id);
    }

    @Operation(summary = "Webhook 回调（Midjourney 主动推送）")
    @PostMapping("/midjourney/notify")
    @SuppressWarnings("unchecked")
    public Result<Void> notify(
            @RequestParam(required = false) String secret,
            @RequestBody Map<String, Object> payload) {
        // M24：验签防伪造——未配置密钥或不匹配一律拒绝，杜绝任意用户伪造完成事件注入 imageUrl
        if (midjourneyService == null || !midjourneyService.verifyNotify(secret)) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN, "回调验签失败");
        }
        String taskId = (String) payload.get("id");
        String status = (String) payload.get("status");
        String imageUrl = (String) payload.get("imageUrl");
        String failReason = (String) payload.get("failReason");
        // buttons 可能是 List<Map>，序列化为 JSON 字符串
        Object buttons = payload.get("buttons");
        String buttonsJson = null;
        if (buttons != null) {
            try {
                buttonsJson = objectMapper.writeValueAsString(buttons);
            } catch (Exception ignored) {
                // 忽略序列化失败
            }
        }
        aiImageService.handleNotify(taskId, status, imageUrl, failReason, buttonsJson);
        return Result.success();
    }

    @Operation(summary = "分页查询图像列表")
    @GetMapping
    public Result<PageResult<AiImageVO>> listImages(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        Long userId = operatorContext.currentUserId().orElseThrow();
        return Result.success(aiImageService.pageByUser(userId, pageNo, pageSize));
    }

    @Operation(summary = "删除图像")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        Long userId = operatorContext.currentUserId().orElseThrow();
        aiImageService.delete(userId, id);
        return Result.success();
    }

    @Operation(summary = "查询我的图像列表（全量，兼容旧接口）")
    @GetMapping("/my")
    public Result<List<AiImageVO>> listMy() {
        Long userId = operatorContext.currentUserId().orElseThrow();
        var list = aiImageService.listByUser(userId).stream().map(aiImageService::toVO).toList();
        return Result.success(list);
    }

    @Operation(summary = "查询单个图像状态（前端轮询用）")
    @GetMapping("/{id}")
    public Result<AiImageVO> getById(@PathVariable Long id) {
        return Result.success(aiImageService.toVO(aiImageService.getById(id)));
    }

    // ========== 通用文生图（通义万象 wanx）==========

    public record DrawRequest(
            @NotBlank String prompt,
            Integer width,
            Integer height,
            /** 模型名，默认 wanx-v1 */
            String model) {}

    @Operation(summary = "文生图（通义万象 wanx，异步）")
    @PostMapping("/draw")
    public Result<Long> draw(@RequestBody @Valid DrawRequest request) {
        Long userId = operatorContext.currentUserId().orElseThrow();
        Long id =
                aiImageService.draw(
                        userId,
                        request.prompt(),
                        request.width(),
                        request.height(),
                        request.model());
        return Result.success(id);
    }

    // ========== 底层代理端点（直接透传 Midjourney API） ==========

    @Operation(summary = "查询单个任务状态（底层）")
    @GetMapping("/midjourney/task/{taskId}")
    public Result<TaskStatus> queryTask(@PathVariable String taskId) {
        if (midjourneyService == null)
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "Midjourney 未启用");
        return Result.success(midjourneyService.queryTask(taskId));
    }

    @Operation(summary = "批量查询任务状态（底层）")
    @PostMapping("/midjourney/tasks")
    public Result<List<TaskStatus>> queryTasks(@RequestBody @Valid BatchQueryRequest request) {
        if (midjourneyService == null)
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "Midjourney 未启用");
        return Result.success(midjourneyService.queryTasks(request.taskIds()));
    }

    // ========== 图생图 / 局部编辑 ==========

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

    @Operation(summary = "图生图（参考图 + 风格 Prompt + 强度）")
    @PostMapping("/image-to-image")
    public Result<ImageResult> imageToImage(@RequestBody @Valid ImageToImageRequest request) {
        Long userId = operatorContext.currentOwnerId().orElse(null);
        var model =
                capabilityRouter.resolve(
                        CapabilityRoutingContext.of(
                                userId,
                                CapabilityRoutingContext.CAP_IMAGE_GEN,
                                request.modelId()));
        var editRequest =
                new ImageEditRequest(
                        request.sourceUrl(),
                        null,
                        request.prompt(),
                        request.strength() != null ? request.strength() : 0.75,
                        model.getModelId());
        var result = imageServiceFactory.getSyncService(model).imageToImage(model, editRequest);
        return Result.success(result);
    }

    @Operation(summary = "局部编辑（原图 + 蒙版 + 编辑 Prompt）")
    @PostMapping("/edit")
    public Result<ImageResult> editImage(@RequestBody @Valid ImageEditDTO request) {
        Long userId = operatorContext.currentOwnerId().orElse(null);
        var model =
                capabilityRouter.resolve(
                        CapabilityRoutingContext.of(
                                userId,
                                CapabilityRoutingContext.CAP_IMAGE_GEN,
                                request.modelId()));
        var editRequest =
                new ImageEditRequest(
                        request.sourceUrl(), request.maskUrl(), request.prompt(), null, model.getModelId());
        var result = imageServiceFactory.getSyncService(model).editImage(model, editRequest);
        return Result.success(result);
    }
}
