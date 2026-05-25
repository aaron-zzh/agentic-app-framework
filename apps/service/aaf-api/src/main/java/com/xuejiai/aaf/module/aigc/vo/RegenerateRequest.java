package com.xuejiai.aaf.module.aigc.vo;

import jakarta.validation.constraints.NotNull;

/**
 * 素材重新生成请求。
 *
 * @param assetId  原始素材 ID
 * @param newPrompt 新提示词（为 null 则沿用原始）
 * @param newSeed   新随机种子（为 null 则随机）
 * @param newStyle  新风格（为 null 则沿用原始）
 */
public record RegenerateRequest(
        @NotNull Long assetId,
        String newPrompt,
        String newSeed,
        String newStyle) {}
