/*
 * Copyright 2024-2026 xuejiai.com & AaronZZH.
 * Licensed under the Apache License, Version 2.0.
 */
package com.xuejiai.aaf.framework.agentscope.tool;

import java.util.function.BiFunction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xuejiai.aaf.framework.agentscope.runtime.AafContextHolder;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;

/**
 * 音乐生成工具——异步模式，与 {@link GenerateImageTool} 同样的 ui_block 机制。
 *
 * <p>参数 requestJson 支持：prompt（必填，描述音乐风格/主题）、model（留空走系统默认）。
 */
public class GenerateMusicTool {

    private static final Logger log = LoggerFactory.getLogger(GenerateMusicTool.class);

    /** (userId, requestJson) → taskId */
    private final BiFunction<Long, String, Long> taskSubmitter;

    public GenerateMusicTool(BiFunction<Long, String, Long> taskSubmitter) {
        this.taskSubmitter = taskSubmitter;
    }

    @Tool(
            description =
                    "根据用户描述异步生成一段音乐。提交后立即返回，生成完成后系统会自动通知。"
                            + "参数 requestJson 必须包含 prompt（描述音乐风格、情绪、乐器等）；可选：model。")
    public String generate_music(
            @ToolParam(
                            name = "requestJson",
                            description = "JSON 参数，如 {\"prompt\":\"轻松愉快的钢琴曲，适合咖啡厅背景音乐\"}")
                    String requestJson) {
        Long userId = AafContextHolder.userId();
        log.info("[GenerateMusicTool] userId={} requestJson={}", userId, requestJson);

        if (requestJson == null || requestJson.isBlank()) {
            return GenerateImageTool.errorJson("requestJson 不能为空，请提供 prompt 参数");
        }

        try {
            Long taskId = taskSubmitter.apply(userId, requestJson);
            return GenerateImageTool.buildUiBlockJson(
                    "music", taskId, GenerateImageTool.extractPrompt(requestJson));
        } catch (Exception e) {
            log.warn("[GenerateMusicTool] 提交失败: {}", e.getMessage());
            return GenerateImageTool.errorJson(e.getMessage());
        }
    }
}
