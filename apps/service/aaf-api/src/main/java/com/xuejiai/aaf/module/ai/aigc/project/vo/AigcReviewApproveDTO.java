package com.xuejiai.aaf.module.ai.aigc.project.vo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record AigcReviewApproveDTO(
        @NotNull Long reviewObjectId,
        @NotNull @PositiveOrZero Integer expectedProjectVersion,
        String conclusion) {}
