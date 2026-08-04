package com.xuejiai.aaf.module.ai.aigc.configuration.vo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** 发布配置版本命令。 */
public record AigcConfigurationPublishDTO(@NotNull @PositiveOrZero Integer expectedVersion) {}
