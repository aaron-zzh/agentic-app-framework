package com.xuejiai.aaf.module.ai.aigc.image.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/** 参数模板更新 Request DTO。 */
public record GenerationTemplateUpdateDTO(
        @Schema(description = "模板名称") String name,
        @Schema(description = "模板分类") String category,
        @Schema(description = "正向提示词") String prompt,
        @Schema(description = "反向提示词") String negativePrompt,
        @Schema(description = "模型名称") String model,
        @Schema(description = "生成宽度（像素）") Integer width,
        @Schema(description = "生成高度（像素）") Integer height,
        @Schema(description = "推理步数") Integer steps,
        @Schema(description = "随机种子") Long seed,
        @Schema(description = "是否公开") Boolean isPublic,
        @Schema(description = "使用场景：GENERATION / PROJECT") String scope) {}
