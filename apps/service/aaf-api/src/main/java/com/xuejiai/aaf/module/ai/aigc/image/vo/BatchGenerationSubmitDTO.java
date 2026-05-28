package com.xuejiai.aaf.module.ai.aigc.image.vo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

/** 批量生成提交 Request DTO。 */
public record BatchGenerationSubmitDTO(
        @Schema(description = "提示词列表", example = "[\"蓝天白云\",\"日落海滩\"]") @NotEmpty
                List<@NotBlank String> prompts,
        @Schema(description = "模型名称", example = "dall-e-3") String model,
        @Schema(description = "生成宽度（像素）", example = "1024") Integer width,
        @Schema(description = "生成高度（像素）", example = "1024") Integer height) {}
