package com.xuejiai.aaf.module.ai.model.vo;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;

/** 更新 AI 模型 Request VO（所有字段可选，null 表示不修改）。 */
@Schema(description = "更新 AI 模型请求")
public record AiModelUpdateDTO(
        @Schema(description = "显示名称") String displayName,
        @Schema(description = "API 地址") String baseUrl,
        @Schema(description = "API Key（null 不修改，空字符串清空）") String apiKey,
        @Schema(description = "能力标签") String capabilities,
        @Schema(description = "温度") Double temperature,
        @Schema(description = "最大 Token 数") Integer maxTokens,
        @Schema(description = "上下文窗口") Integer contextWindow,
        @Schema(description = "输入价格（每千 Token）") BigDecimal inputPricePerK,
        @Schema(description = "输出价格（每千 Token）") BigDecimal outputPricePerK,
        @Schema(description = "是否启用") Boolean enabled,
        @Schema(description = "降级模型标识") String fallbackModelId,
        @Schema(description = "排序") Integer sortOrder,
        @Schema(description = "备注") String remark) {}
