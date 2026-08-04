package com.xuejiai.aaf.module.ai.aigc.brand.vo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** 发布品牌资料版本命令。 */
public record AigcBrandProfileVersionPublishDTO(
        @NotNull @PositiveOrZero Integer expectedProfileVersion,
        @NotNull @PositiveOrZero Integer expectedVersion) {}
