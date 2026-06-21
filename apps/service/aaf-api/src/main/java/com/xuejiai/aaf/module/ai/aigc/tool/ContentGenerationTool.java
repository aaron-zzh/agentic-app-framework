package com.xuejiai.aaf.module.ai.aigc.tool;

import java.util.List;
import java.util.Map;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.engine.tool.ToolCallDispatcher.ToolCallResult;
import com.xuejiai.aaf.framework.intelligent.ai.safety.ContentSafetyRequest;
import com.xuejiai.aaf.framework.intelligent.ai.safety.ContentSafetyService;
import com.xuejiai.aaf.framework.intelligent.ai.video.VideoGenerationService;
import com.xuejiai.aaf.framework.intelligent.ai.video.vo.VideoRequest;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRouter;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRoutingContext;
import com.xuejiai.aaf.framework.intelligent.core.registry.AiServiceRegistry;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.ai.aigc.media.enums.MediaAssetType;
import com.xuejiai.aaf.module.ai.aigc.media.service.MediaAssetService;
import com.xuejiai.aaf.module.ai.aigc.media.vo.SaveFromGenerationDTO;
import com.xuejiai.aaf.module.ai.aigc.task.service.AigcTaskService;
import com.xuejiai.aaf.module.ai.aigc.task.vo.ImageTaskRequest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 暴露给 AI 的生成式内容工具。目录开放、权限、积分和确认由 ai_tool_catalog 控制。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContentGenerationTool {

    private static final String IMAGE_TOOL = "generateImage";
    private static final String VIDEO_TOOL = "generateVideo";

    private final AigcTaskService aigcTaskService;
    private final AiServiceRegistry aiServiceRegistry;
    private final CapabilityRouter capabilityRouter;
    private final MediaAssetService mediaAssetService;
    private final ObjectProvider<ContentSafetyService> contentSafetyService;
    private final OperatorContext operatorContext;

    @Tool(description = "生成图片。参数为 JSON：prompt 必填，width/height/model 可选。")
    public String generateImage(@ToolParam(description = "图片生成 JSON 参数") String requestJson) {
        try {
            var request = JsonUtils.parseObject(requestJson, ImageGenerateRequest.class);
            var safety =
                    review(
                            IMAGE_TOOL,
                            "IMAGE_GENERATION",
                            request.prompt(),
                            Map.of("model", value(request.model())));
            if (!safety.allowed()) {
                return blockedBySafety(
                        IMAGE_TOOL, safety.code(), safety.message(), safety.reviewId());
            }
            var userId = operatorContext.currentOwnerId().orElseThrow();
            var taskId =
                    aigcTaskService.submitImageTask(
                            userId,
                            new ImageTaskRequest(
                                    request.prompt(),
                                    request.model(),
                                    request.width() != null ? request.width() : 1024,
                                    request.height() != null ? request.height() : 1024,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    request.prompt(),
                                    null,
                                    null,
                                    null));
            return asJson(
                    ToolCallResult.success(
                            IMAGE_TOOL,
                            JsonUtils.toJsonString(
                                    Map.of("imageId", taskId, "status", "PENDING"))));
        } catch (Exception ex) {
            return asJson(ToolCallResult.error(IMAGE_TOOL, "GENERATION_ERROR", ex.getMessage()));
        }
    }

    @Tool(
            description =
                    "生成视频。参数为 JSON：prompt 必填，imageUrl/referenceImageUrls/model/resolution/ratio/duration/seed 可选。")
    public String generateVideo(@ToolParam(description = "视频生成 JSON 参数") String requestJson) {
        try {
            var request = JsonUtils.parseObject(requestJson, VideoGenerateRequest.class);
            var safety =
                    review(
                            VIDEO_TOOL,
                            "VIDEO_GENERATION",
                            request.prompt(),
                            Map.of("model", value(request.model())));
            if (!safety.allowed()) {
                return blockedBySafety(
                        VIDEO_TOOL, safety.code(), safety.message(), safety.reviewId());
            }
            // 走决策链选模型，再通过 factory 路由到正确实现
            var userId = operatorContext.currentOwnerId().orElse(null);
            var ctx =
                    CapabilityRoutingContext.of(
                            userId, CapabilityRoutingContext.CAP_VIDEO_GEN, request.model());
            var aiModel = capabilityRouter.resolve(ctx);
            var service = aiServiceRegistry.get(VideoGenerationService.class, aiModel);

            var taskId =
                    service.submit(
                            new VideoRequest(
                                    request.prompt(),
                                    request.imageUrl(),
                                    request.referenceImageUrls(),
                                    request.model(),
                                    request.resolution(),
                                    request.ratio(),
                                    request.duration(),
                                    request.seed(),
                                    null));

            // 自动保存到素材库（视频为异步任务，先记录 taskId）
            try {
                mediaAssetService.saveFromGeneration(
                        userId != null ? userId : 0L,
                        new SaveFromGenerationDTO(
                                null,
                                MediaAssetType.VIDEO,
                                "pending://" + taskId,
                                null,
                                "{\"prompt\":\"%s\",\"taskId\":\"%s\"}"
                                        .formatted(request.prompt().replace("\"", "\\\""), taskId),
                                null,
                                null,
                                null,
                                null,
                                null,
                                true,
                                null,
                                null,
                                null));
            } catch (Exception e) {
                log.warn("自动保存素材库失败: {}", e.getMessage());
            }

            return asJson(
                    ToolCallResult.success(
                            VIDEO_TOOL,
                            JsonUtils.toJsonString(Map.of("taskId", taskId, "status", "PENDING"))));
        } catch (Exception ex) {
            return asJson(ToolCallResult.error(VIDEO_TOOL, "GENERATION_ERROR", ex.getMessage()));
        }
    }

    private com.xuejiai.aaf.framework.intelligent.ai.safety.ContentSafetyResult review(
            String toolName, String category, String prompt, Map<String, Object> metadata) {
        var service = contentSafetyService.getIfAvailable();
        if (service == null) {
            // 无安全服务时默认放行
            return com.xuejiai.aaf.framework.intelligent.ai.safety.ContentSafetyResult.pass();
        }
        return service.reviewBeforeGeneration(
                new ContentSafetyRequest(
                        toolName,
                        category,
                        null,
                        operatorContext.currentOwnerId().orElse(null),
                        prompt,
                        metadata));
    }

    private String blockedBySafety(String toolName, String code, String message, String reviewId) {
        if ("PENDING_CONTENT_REVIEW".equals(code)) {
            return asJson(ToolCallResult.pendingContentReview(toolName, message, reviewId));
        }
        return asJson(ToolCallResult.error(toolName, code, message));
    }

    private String asJson(ToolCallResult result) {
        try {
            return JsonUtils.toJsonString(result);
        } catch (Exception ex) {
            return "{\"success\":false,\"code\":\"TOOL_RESULT_SERIALIZE_ERROR\",\"message\":\"工具结果序列化失败\"}";
        }
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    public record ImageGenerateRequest(
            String prompt, Integer width, Integer height, String model) {}

    public record VideoGenerateRequest(
            String prompt,
            String imageUrl,
            List<String> referenceImageUrls,
            String model,
            String resolution,
            String ratio,
            Integer duration,
            Integer seed) {}
}
