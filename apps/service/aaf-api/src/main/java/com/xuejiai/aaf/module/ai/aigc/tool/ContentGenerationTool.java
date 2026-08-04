package com.xuejiai.aaf.module.ai.aigc.tool;

import java.util.List;
import java.util.Map;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.engine.tool.ToolCallDispatcher.ToolCallResult;
import com.xuejiai.aaf.framework.intelligent.ai.safety.ContentSafetyRequest;
import com.xuejiai.aaf.framework.intelligent.ai.safety.ContentSafetyService;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.ai.aigc.task.api.AigcTaskApi;
import com.xuejiai.aaf.module.ai.aigc.task.api.AigcTaskSubmitCommand;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 暴露给 AI 的生成式内容工具。目录开放、权限、积分和确认由 ai_tool_catalog 控制。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContentGenerationTool {

    private static final String IMAGE_TOOL = "generateImage";
    private static final String VIDEO_TOOL = "generateVideo";

    private final AigcTaskApi taskApi;
    private final ContentSafetyService contentSafetyService;
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
            var parameters = new java.util.LinkedHashMap<String, Object>();
            if (request.width() != null) parameters.put("width", request.width());
            if (request.height() != null) parameters.put("height", request.height());
            var taskId =
                    taskApi.submit(
                                    new AigcTaskSubmitCommand(
                                            null,
                                            null,
                                            null,
                                            "IMAGE",
                                            request.model(),
                                            request.prompt(),
                                            JsonUtils.toJsonString(parameters),
                                            java.util.UUID.randomUUID().toString()))
                            .id();
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
            var imageMode = videoImageMode(request);
            var parameters = new java.util.LinkedHashMap<String, Object>();
            if (request.resolution() != null) parameters.put("resolution", request.resolution());
            if (request.duration() != null) parameters.put("duration", request.duration());
            if (request.ratio() != null) parameters.put("ratio", request.ratio());
            if (request.seed() != null) parameters.put("seed", request.seed());
            parameters.put("imageMode", imageMode);
            if (request.imageUrl() != null) parameters.put("imageUrl", request.imageUrl());
            if (request.referenceImageUrls() != null) {
                parameters.put("referenceImageUrls", request.referenceImageUrls());
            }
            var taskId =
                    taskApi.submit(
                                    new AigcTaskSubmitCommand(
                                            null,
                                            null,
                                            null,
                                            "VIDEO",
                                            request.model(),
                                            request.prompt(),
                                            JsonUtils.toJsonString(parameters),
                                            java.util.UUID.randomUUID().toString()))
                            .id();

            return asJson(
                    ToolCallResult.success(
                            VIDEO_TOOL,
                            JsonUtils.toJsonString(Map.of("taskId", taskId, "status", "PENDING"))));
        } catch (Exception ex) {
            return asJson(ToolCallResult.error(VIDEO_TOOL, "GENERATION_ERROR", ex.getMessage()));
        }
    }

    private String videoImageMode(VideoGenerateRequest request) {
        if (request.referenceImageUrls() != null && !request.referenceImageUrls().isEmpty()) {
            return "REFERENCE";
        }
        if (request.imageUrl() != null && !request.imageUrl().isBlank()) {
            return "FIRST_FRAME";
        }
        return "T2V";
    }

    private com.xuejiai.aaf.framework.intelligent.ai.safety.ContentSafetyResult review(
            String toolName, String category, String prompt, Map<String, Object> metadata) {
        return contentSafetyService.reviewBeforeGeneration(
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
