/*
 * Copyright 2024-2026 xuejiai.com & AaronZZH.
 * Licensed under the Apache License, Version 2.0.
 */
package com.xuejiai.aaf.framework.agentscope.tool;

import java.util.function.BiFunction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.agentscope.runtime.AafContextHolder;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import tools.jackson.databind.node.ObjectNode;

/**
 * 图像生成工具——异步模式。
 *
 * <p>提交任务后立即返回带 {@code __ui__:true} 标记的生成中卡片 JSON， 由 {@link
 * com.xuejiai.aaf.framework.agentscope.middleware.UiEventMiddleware} 检测后 发出 {@code
 * CustomEvent("ui_block")} 到前端 SSE 流，前端渲染生成状态卡片。 任务完成后通过 AigcTaskEventService 的独立 SSE 推送更新。
 *
 * <p>不轮询等待，不阻塞 Agent 推理循环。
 */
public class GenerateImageTool {

    private static final Logger log = LoggerFactory.getLogger(GenerateImageTool.class);

    /** (userId, requestJson) → taskId */
    private final BiFunction<Long, String, Long> taskSubmitter;

    public GenerateImageTool(BiFunction<Long, String, Long> taskSubmitter) {
        this.taskSubmitter = taskSubmitter;
    }

    @Tool(
            description =
                    "根据用户描述异步生成一张图片。提交后立即返回，生成完成后系统会自动通知。"
                            + "参数 requestJson 是 JSON 字符串，必须包含 prompt；"
                            + "可选：width（默认1024）、height（默认1024）、model（留空走系统默认）、aspectRatio（如16:9）。")
    public String generate_image(
            @ToolParam(
                            name = "requestJson",
                            description =
                                    "JSON 参数，如 {\"prompt\":\"一只橙色的猫坐在月球上\",\"aspectRatio\":\"1:1\"}")
                    String requestJson) {
        Long userId = AafContextHolder.userId();
        log.info("[GenerateImageTool] userId={} requestJson={}", userId, requestJson);

        if (requestJson == null || requestJson.isBlank()) {
            return errorJson("requestJson 不能为空，请提供 prompt 参数");
        }

        try {
            Long taskId = taskSubmitter.apply(userId, requestJson);
            return buildUiBlockJson("image", taskId, extractPrompt(requestJson));
        } catch (Exception e) {
            log.warn("[GenerateImageTool] 提交失败: {}", e.getMessage());
            return errorJson(e.getMessage());
        }
    }

    /** 构建带 __ui__:true 的生成中卡片 JSON */
    static String buildUiBlockJson(String mediaType, Long taskId, String prompt) {
        try {
            ObjectNode node = JsonUtils.createObjectNode();
            node.put(SendUiTool.UI_MARKER, true);
            node.put("uiType", "aigc_task");
            node.put("taskId", taskId);
            node.put("mediaType", mediaType); // image / video / music
            node.put("status", "PENDING");
            node.put("prompt", prompt != null ? prompt : "");
            node.put("message", mediaTypeLabel(mediaType) + "生成中，请稍候…");
            return JsonUtils.toJsonString(node);
        } catch (Exception e) {
            return errorJson("构建 UI 块失败");
        }
    }

    static String extractPrompt(String requestJson) {
        try {
            return JsonUtils.readTree(requestJson).path("prompt").asText("");
        } catch (Exception e) {
            return "";
        }
    }

    static String mediaTypeLabel(String mediaType) {
        return switch (mediaType) {
            case "video" -> "视频";
            case "music" -> "音乐";
            default -> "图片";
        };
    }

    static String errorJson(String msg) {
        return "{\"status\":\"error\",\"message\":\""
                + (msg == null ? "生成失败" : msg.replace("\"", "\\\"").replace("\n", " "))
                + "\"}";
    }
}
