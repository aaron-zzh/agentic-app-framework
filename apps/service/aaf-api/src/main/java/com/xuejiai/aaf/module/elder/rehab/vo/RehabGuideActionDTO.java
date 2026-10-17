package com.xuejiai.aaf.module.elder.rehab.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 康复指导单条动作请求。 */
@Schema(description = "康复指导单条动作请求")
public record RehabGuideActionDTO(@NotNull @Positive @Schema(description = "康复指导 ID") Long id) {}
