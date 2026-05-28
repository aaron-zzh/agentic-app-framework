package com.xuejiai.aaf.module.ai.aigc.media.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** 素材重新生成 Request DTO。 */
public record RegenerateRequest(
        @Schema(description = "原始素材 ID", example = "1") @NotNull Long assetId,
        @Schema(description = "新提示词（为 null 则沿用原始）", example = "蓝天白云") String newPrompt,
        @Schema(description = "新随机种子（为 null 则随机）", example = "12345") String newSeed,
        @Schema(description = "新风格（为 null 则沿用原始）", example = "写实") String newStyle) {}
