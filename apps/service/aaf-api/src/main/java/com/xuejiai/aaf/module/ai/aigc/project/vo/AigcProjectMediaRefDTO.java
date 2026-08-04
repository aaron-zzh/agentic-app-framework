package com.xuejiai.aaf.module.ai.aigc.project.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record AigcProjectMediaRefDTO(
        Long objectId,
        @NotNull Long mediaVersionId,
        @NotBlank String role,
        Integer sortOrder,
        String adoptionStatus,
        @NotNull @PositiveOrZero Integer expectedProjectVersion) {}
