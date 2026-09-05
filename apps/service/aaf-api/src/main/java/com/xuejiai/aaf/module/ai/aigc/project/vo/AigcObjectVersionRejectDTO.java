package com.xuejiai.aaf.module.ai.aigc.project.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AigcObjectVersionRejectDTO(
        @NotNull Integer expectedProjectVersion,
        @NotBlank String reason,
        @NotBlank String idempotencyKey) {}
