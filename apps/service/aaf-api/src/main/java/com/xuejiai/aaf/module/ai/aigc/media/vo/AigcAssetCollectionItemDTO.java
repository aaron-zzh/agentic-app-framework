package com.xuejiai.aaf.module.ai.aigc.media.vo;

import jakarta.validation.constraints.NotNull;

/** 添加或更新集合成员命令。 */
public record AigcAssetCollectionItemDTO(@NotNull Long assetId, String role, Integer sortOrder) {}
