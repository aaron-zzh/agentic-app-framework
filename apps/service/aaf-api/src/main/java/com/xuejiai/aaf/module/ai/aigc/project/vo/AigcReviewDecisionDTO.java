package com.xuejiai.aaf.module.ai.aigc.project.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AigcReviewDecisionDTO(
        @NotNull Long expectedManifestObjectVersionId,
        @NotNull Integer expectedProjectVersion,
        @NotNull Integer expectedReviewVersion,
        String comment,
        @NotBlank String idempotencyKey) {}
