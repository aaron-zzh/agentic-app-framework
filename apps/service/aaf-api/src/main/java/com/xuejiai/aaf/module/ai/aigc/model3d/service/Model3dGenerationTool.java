package com.xuejiai.aaf.module.ai.aigc.model3d.service;

import java.util.function.Function;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.ai.aigc.task.service.AigcTaskService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 3D 模型生成工具 — 注册为 Spring AI Function，可被对话中的 AI 调用。
 *
 * @author AaronZZH & Kiro
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class Model3dGenerationTool {

    private final AigcTaskService aigcTaskService;
    private final OperatorContext operatorContext;

    public record Request(String prompt, String textureQuality) {}

    public record Response(Long taskId, String status, String message) {}

    @Bean
    @Description("根据用户描述生成 3D 模型。参数：prompt(必填)-模型描述, textureQuality(可选)-贴图质量standard/detailed")
    public Function<Request, Response> generate3dModel() {
        return request -> {
            log.info("对话触发 3D 模型生成: prompt={}", request.prompt());
            try {
                var userId = operatorContext.currentOwnerId().orElseThrow();
                var taskId =
                        aigcTaskService.submit3dTask(
                                userId,
                                request.prompt(),
                                null,
                                "text",
                                request.textureQuality(),
                                null);
                return new Response(taskId, "PENDING", "3D 模型生成任务已提交，预计2-10分钟完成");
            } catch (Exception e) {
                log.error("对话生成 3D 模型失败: {}", e.getMessage(), e);
                return new Response(null, "FAILED", "生成失败: " + e.getMessage());
            }
        };
    }
}
