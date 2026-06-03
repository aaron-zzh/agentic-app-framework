package com.xuejiai.aaf.module.ai.model.vo;

import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;

/** AI 模型 JSON 导入结果。 */
@Schema(description = "AI 模型 JSON 导入结果")
public record AiModelImportResultVO(
        @Schema(description = "解析到的模型数") int parsedCount,
        @Schema(description = "按分组前 10 条筛选后的模型数") int selectedCount,
        @Schema(description = "新增模型数") int createdCount,
        @Schema(description = "更新模型数") int updatedCount,
        @Schema(description = "供应商编码") String providerCode,
        @Schema(description = "各分组实际选中模型数") Map<String, Integer> groupCounts,
        @Schema(description = "导入的模型标识") List<String> modelIds) {}
