package com.xuejiai.aaf.module.ai.aigc.image.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/** 参数模板 Response VO。 */
public record GenerationTemplateVO(
        @Schema(description = "模板 ID", example = "1") Long id,
        @Schema(description = "模板类型", example = "IMAGE") String type,
        @Schema(description = "模板名称", example = "风景写实") String name,
        @Schema(description = "模板分类", example = "风景") String category,
        @Schema(description = "正向提示词", example = "蓝天白云，高山流水") String prompt,
        @Schema(description = "反向提示词", example = "模糊，低质量") String negativePrompt,
        @Schema(description = "模型名称", example = "dall-e-3") String model,
        @Schema(description = "生成宽度（像素）", example = "1024") Integer width,
        @Schema(description = "生成高度（像素）", example = "1024") Integer height,
        @Schema(description = "推理步数", example = "30") Integer steps,
        @Schema(description = "随机种子", example = "12345") Long seed,
        @Schema(description = "是否公开", example = "true") Boolean isPublic,
        @Schema(description = "使用次数", example = "42") Integer usageCount,
        @Schema(description = "使用场景", example = "GENERATION") String scope) {}
