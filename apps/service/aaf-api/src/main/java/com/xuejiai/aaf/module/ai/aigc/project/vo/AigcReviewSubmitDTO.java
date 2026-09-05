package com.xuejiai.aaf.module.ai.aigc.project.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AigcReviewSubmitDTO(
        @NotNull Long setObjectId,
        @NotNull Long manifestObjectVersionId,
        @NotNull Integer expectedProjectVersion,
        @NotBlank String idempotencyKey) {}
