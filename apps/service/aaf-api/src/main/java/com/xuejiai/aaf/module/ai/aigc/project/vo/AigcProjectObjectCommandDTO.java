package com.xuejiai.aaf.module.ai.aigc.project.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record AigcProjectObjectCommandDTO(
        Long parentObjectId,
        String blueprintTemplateKey,
        @NotBlank String objectType,
        @NotBlank String displayName,
        @NotBlank String contractRole,
        Integer orderNo,
        String schemaVersion,
        String payloadJson,
        @NotNull @PositiveOrZero Integer expectedProjectVersion) {}
