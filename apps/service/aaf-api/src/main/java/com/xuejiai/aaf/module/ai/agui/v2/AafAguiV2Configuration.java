/*
 * Copyright 2024-2026 xuejiai.com & AaronZZH.
 * Licensed under the Apache License, Version 2.0.
 */
package com.xuejiai.aaf.module.ai.agui.v2;

import java.util.function.BiFunction;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.JsonNode;
import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.agentscope.runtime.ConversationContextResolver;
import com.xuejiai.aaf.framework.agentscope.tool.GenerateImageTool;
import com.xuejiai.aaf.framework.agentscope.tool.GenerateMusicTool;
import com.xuejiai.aaf.framework.agentscope.tool.GenerateVideoTool;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.ai.aigc.task.repository.AigcTaskRepository;
import com.xuejiai.aaf.module.ai.aigc.task.service.AigcTaskService;
import com.xuejiai.aaf.module.ai.aigc.task.vo.ImageTaskRequest;
import com.xuejiai.aaf.module.ai.aigc.task.vo.VideoTaskRequest;

import io.agentscope.core.agui.registry.AguiAgentRegistry;
import io.agentscope.spring.boot.agui.common.AguiProperties;
import io.agentscope.spring.boot.agui.common.ThreadSessionManager;
import io.agentscope.spring.boot.agui.mvc.AguiMvcController;
import io.agentscope.spring.boot.agui.mvc.AguiRestController;

/**
 * AAF V2 AG-UI 配置：注册 {@link AafAguiV2RestController} 替换 starter 默认的 {@link AguiRestController}。
 *
 * <p>注：{@code agentscope-agui-spring-boot-starter} 的 AguiRestController 用
 * {@code @ConditionalOnMissingBean} 标注，所以我们提供同类型 Bean 即可让默认实现自动让位。
 */
@Configuration
public class AafAguiV2Configuration {

    @Bean
    public AguiRestController aguiRestController(
            AguiMvcController aguiMvcController,
            AguiAgentRegistry aguiAgentRegistry,
            ThreadSessionManager threadSessionManager,
            AguiProperties props,
            OperatorContext operatorContext,
            ConversationContextResolver contextResolver) {
        return new AafAguiV2RestController(
                aguiMvcController,
                aguiAgentRegistry,
                threadSessionManager,
                props,
                operatorContext,
                contextResolver);
    }

    /**
     * 图像生成工具 Bean——异步提交，返回 ui_block 卡片，不轮询等待。
     */
    @Bean
    public GenerateImageTool generateImageTool(AigcTaskService aigcTaskService) {
        BiFunction<Long, String, Long> submitter = (userId, requestJson) ->
                aigcTaskService.submitImageTask(userId, parseImageRequest(requestJson));
        return new GenerateImageTool(submitter);
    }

    /**
     * 视频生成工具 Bean——异步提交。
     */
    @Bean
    public GenerateVideoTool generateVideoTool(AigcTaskService aigcTaskService) {
        BiFunction<Long, String, Long> submitter = (userId, requestJson) -> {
            JsonNode n = parseJson(requestJson);
            String prompt = n.path("prompt").asText("");
            if (prompt.isBlank()) throw new IllegalArgumentException("prompt 不能为空");
            String model = nullIfBlank(n.path("model").asText(null));
            String ratio = nullIfBlank(n.path("ratio").asText(null));
            int duration = n.path("duration").asInt(5);
            String imageUrl = nullIfBlank(n.path("imageUrl").asText(null));
            var req = new VideoTaskRequest(prompt, model, null, null, duration,
                    ratio, null, imageUrl != null ? "FIRST_FRAME" : "T2V",
                    imageUrl, null, null, null, null, null, null);
            return aigcTaskService.submitVideoTask(userId, req);
        };
        return new GenerateVideoTool(submitter);
    }

    /**
     * 音乐生成工具 Bean——异步提交。
     */
    @Bean
    public GenerateMusicTool generateMusicTool(AigcTaskService aigcTaskService) {
        BiFunction<Long, String, Long> submitter = (userId, requestJson) -> {
            JsonNode n = parseJson(requestJson);
            String prompt = n.path("prompt").asText("");
            if (prompt.isBlank()) throw new IllegalArgumentException("prompt 不能为空");
            String model = nullIfBlank(n.path("model").asText(null));
            return aigcTaskService.submitMusicTask(userId, prompt, model, null, null, null);
        };
        return new GenerateMusicTool(submitter);
    }

    private static ImageTaskRequest parseImageRequest(String json) {
        JsonNode n = parseJson(json);
        String prompt = n.path("prompt").asText("");
        if (prompt.isBlank()) throw new IllegalArgumentException("prompt 不能为空");
        int width = n.path("width").asInt(1024);
        int height = n.path("height").asInt(1024);
        String model = nullIfBlank(n.path("model").asText(null));
        String aspectRatio = nullIfBlank(n.path("aspectRatio").asText(null));
        return new ImageTaskRequest(prompt, model, width, height, null, null, null, 1,
                null, null, null, null, null, null, aspectRatio, prompt, null, null, null);
    }

    private static JsonNode parseJson(String json) {
        try {
            return JsonUtils.parseObject(json, JsonNode.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("JSON 解析失败: " + e.getMessage());
        }
    }

    private static String nullIfBlank(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
