package com.xuejiai.aaf.module.ai.aigc.project.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record AigcDocumentCandidateDTO(
        @NotNull @PositiveOrZero Integer expectedProjectVersion,
        @NotNull @PositiveOrZero Long expectedGraphRevision,
        String title,
        @NotBlank String content,
        String format,
        String summary,
        @NotBlank String idempotencyKey) {}
