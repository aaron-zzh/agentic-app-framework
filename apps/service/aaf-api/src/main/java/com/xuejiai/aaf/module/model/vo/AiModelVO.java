package com.xuejiai.aaf.module.model.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/** AI 模型信息 Response VO（不含 apiKey 明文）。 */
@Schema(description = "AI 模型信息")
public record AiModelVO(
        @Schema(description = "编号", example = "1") Long id,
        @Schema(description = "模型标识", example = "gpt-4o") String modelId,
        @Schema(description = "显示名称", example = "GPT-4o") String displayName,
        @Schema(description = "厂商", example = "OpenAI") String provider,
        @Schema(description = "厂商类型", example = "OPENAI_COMPAT") String providerType,
        @Schema(description = "模型名称", example = "gpt-4o") String modelName,
        @Schema(description = "API 地址", example = "https://api.openai.com/v1") String baseUrl,
        @Schema(description = "apiKey 是否已配置") boolean apiKeyConfigured,
        @Schema(description = "能力标签", example = "CHAT") String capabilities,
        @Schema(description = "温度", example = "0.7") Double temperature,
        @Schema(description = "最大 Token 数", example = "4096") Integer maxTokens,
        @Schema(description = "上下文窗口", example = "128000") Integer contextWindow,
        @Schema(description = "输入价格（每千 Token）", example = "0.005") BigDecimal inputPricePerK,
        @Schema(description = "输出价格（每千 Token）", example = "0.015") BigDecimal outputPricePerK,
        @Schema(description = "是否启用") Boolean enabled,
        @Schema(description = "降级模型标识") String fallbackModelId,
        @Schema(description = "排序", example = "100") Integer sortOrder,
        @Schema(description = "备注") String remark,
        @Schema(description = "创建时间") LocalDateTime createTime,
        @Schema(description = "更新时间") LocalDateTime updateTime) {}
