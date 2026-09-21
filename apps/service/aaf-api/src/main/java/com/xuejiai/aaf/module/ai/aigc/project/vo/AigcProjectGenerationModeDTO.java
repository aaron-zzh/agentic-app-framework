package com.xuejiai.aaf.module.ai.aigc.project.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** 协作模式切换请求；不接受执行范围、目标或预算参数。 */
public record AigcProjectGenerationModeDTO(
        @NotBlank @Pattern(regexp = "manual|auto") String mode,
        @NotNull Integer expectedProjectVersion,
        @NotBlank String idempotencyKey) {}
