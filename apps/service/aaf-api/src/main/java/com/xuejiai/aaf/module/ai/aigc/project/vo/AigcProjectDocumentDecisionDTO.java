package com.xuejiai.aaf.module.ai.aigc.project.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** 项目文档 candidate 采用或否决请求。 */
public record AigcProjectDocumentDecisionDTO(
        @NotNull @PositiveOrZero Integer expectedProjectVersion,
        @NotNull @Positive Long expectedSelectedDocumentVersionId,
        boolean confirmedReplacement,
        @Size(max = 1000) String reason,
        @NotBlank @Size(max = 100) String idempotencyKey) {}
