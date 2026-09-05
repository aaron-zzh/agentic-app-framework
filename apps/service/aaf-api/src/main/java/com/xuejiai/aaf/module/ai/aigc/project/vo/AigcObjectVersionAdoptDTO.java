package com.xuejiai.aaf.module.ai.aigc.project.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AigcObjectVersionAdoptDTO(
        Long expectedAdoptedVersionId,
        @NotNull Integer expectedProjectVersion,
        boolean confirmedReplacement,
        String reason,
        @NotBlank String idempotencyKey) {}
