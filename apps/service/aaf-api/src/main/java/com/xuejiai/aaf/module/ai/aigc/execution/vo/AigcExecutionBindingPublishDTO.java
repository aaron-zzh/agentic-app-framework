package com.xuejiai.aaf.module.ai.aigc.execution.vo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** 执行绑定发布请求。 */
public record AigcExecutionBindingPublishDTO(@NotNull @PositiveOrZero Integer expectedVersion) {}
