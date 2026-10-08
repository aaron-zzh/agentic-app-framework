package com.xuejiai.aaf.module.digitalhuman.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/** 数字人大模型配置。 */
@Schema(description = "模型配置")
public record DigitalHumanModelConfig(
        @Schema(description = "提供商", example = "anthropic") String provider,
        @Schema(description = "模型名称", example = "claude-sonnet-5") String model,
        @Schema(description = "温度", example = "0.7") Double temperature,
        @Schema(description = "最大 Token 数", example = "2000") Integer maxTokens,
        @Schema(description = "系统提示词") String systemPrompt) {}
