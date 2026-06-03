package com.xuejiai.aaf.module.ai.aigc.tool;

import java.util.List;
import java.util.Map;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.xuejiai.aaf.framework.engine.tool.ToolCallDispatcher.ToolCallResult;
import com.xuejiai.aaf.framework.intelligent.ai.safety.ContentSafetyRequest;
import com.xuejiai.aaf.framework.intelligent.ai.safety.ContentSafetyService;
import com.xuejiai.aaf.framework.intelligent.ai.video.VideoGenerationService;
import com.xuejiai.aaf.framework.intelligent.ai.video.VideoGenerationService.VideoRequest;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.ai.aigc.image.service.AiImageService;

import lombok.RequiredArgsConstructor;

/** 暴露给 AI 的生成式内容工具。目录开放、权限、积分和确认由 ai_tool_catalog 控制。 */
@Component
@RequiredArgsConstructor
public class ContentGenerationTool {

    private static final String IMAGE_TOOL = "generateImage";
    private static final String VIDEO_TOOL = "generateVideo";

    private final AiImageService aiImageService;
    private final ObjectProvider<VideoGenerationService> videoGenerationService;
    private final ObjectProvider<ContentSafetyService> contentSafetyService;
    private final OperatorContext operatorContext;
    private final ObjectMapper objectMapper;

    @Tool(description = "生成图片。参数为 JSON：prompt 必填，width/height/model 可选。")
    public String generateImage(@ToolParam(description = "图片生成 JSON 参数") String requestJson) {
        try {
            var request = objectMapper.readValue(requestJson, ImageGenerateRequest.class);
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
            var imageId =
                    aiImageService.draw(
                            userId,
                            request.prompt(),
                            request.width(),
                            request.height(),
                            request.model());
            return asJson(
                    ToolCallResult.success(
                            IMAGE_TOOL,
                            objectMapper.writeValueAsString(
                                    Map.of("imageId", imageId, "status", "PENDING"))));
        } catch (Exception ex) {
            return asJson(ToolCallResult.error(IMAGE_TOOL, "GENERATION_ERROR", ex.getMessage()));
        }
    }

    @Tool(
            description =
                    "生成视频。参数为 JSON：prompt 必填，imageUrl/referenceImageUrls/model/resolution/ratio/duration/seed 可选。")
    public String generateVideo(@ToolParam(description = "视频生成 JSON 参数") String requestJson) {
        try {
            var service = videoGenerationService.getIfAvailable();
            if (service == null) {
                return asJson(ToolCallResult.error(VIDEO_TOOL, "TOOL_UNAVAILABLE", "视频生成服务未启用"));
            }
            var request = objectMapper.readValue(requestJson, VideoGenerateRequest.class);
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
                                    request.seed()));
            return asJson(
                    ToolCallResult.success(
                            VIDEO_TOOL,
                            objectMapper.writeValueAsString(
                                    Map.of("taskId", taskId, "status", "PENDING"))));
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
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException ex) {
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
