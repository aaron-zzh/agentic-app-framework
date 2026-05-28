package com.xuejiai.aaf.module.ai.model.vo;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 创建 AI 模型 Request VO。 */
@Schema(description = "创建 AI 模型请求")
public record AiModelCreateDTO(
        @Schema(
                        description = "模型标识",
                        requiredMode = Schema.RequiredMode.REQUIRED,
                        example = "gpt-4o")
                @NotBlank
                String modelId,
        @Schema(
                        description = "显示名称",
                        requiredMode = Schema.RequiredMode.REQUIRED,
                        example = "GPT-4o")
                @NotBlank
                String displayName,
        @Schema(description = "厂商", requiredMode = Schema.RequiredMode.REQUIRED, example = "OpenAI")
                @NotBlank
                String provider,
        @Schema(
                        description = "厂商类型",
                        requiredMode = Schema.RequiredMode.REQUIRED,
                        example = "OPENAI_COMPAT")
                @NotBlank
                @Pattern(
                        regexp = "^(OPENAI_COMPAT|ANTHROPIC|OLLAMA)$",
                        message = "providerType 必须为 OPENAI_COMPAT / ANTHROPIC / OLLAMA")
                String providerType,
        @Schema(
                        description = "模型名称",
                        requiredMode = Schema.RequiredMode.REQUIRED,
                        example = "gpt-4o")
                @NotBlank
                String modelName,
        @Schema(description = "API 地址", example = "https://api.openai.com/v1") String baseUrl,
        @Schema(description = "API Key（明文，存储时加密）") String apiKey,
        @Schema(description = "能力标签", example = "CHAT") String capabilities,
        @Schema(description = "温度", example = "0.7") Double temperature,
        @Schema(description = "最大 Token 数", example = "4096") Integer maxTokens,
        @Schema(description = "上下文窗口", example = "128000") Integer contextWindow,
        @Schema(description = "输入价格（每千 Token）", example = "0.005") BigDecimal inputPricePerK,
        @Schema(description = "输出价格（每千 Token）", example = "0.015") BigDecimal outputPricePerK,
        @Schema(description = "降级模型标识") String fallbackModelId,
        @Schema(description = "排序", example = "100") Integer sortOrder,
        @Schema(description = "备注") String remark) {}
