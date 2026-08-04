package com.xuejiai.aaf.module.ai.aigc.project.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record AigcProjectObjectCommandDTO(
        Long parentObjectId,
        @NotBlank String stableKey,
        @NotBlank String objectType,
        Integer orderNo,
        String schemaVersion,
        String payloadJson,
        @NotNull @PositiveOrZero Integer expectedProjectVersion) {}
