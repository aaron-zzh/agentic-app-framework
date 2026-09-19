package com.xuejiai.aaf.module.ai.aigc.project.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** 项目自产文档新版本 candidate 请求。 */
public record AigcProjectDocumentCandidateDTO(
        @Size(max = 200) String title,
        @NotBlank String content,
        @Size(max = 32) String format,
        @Size(max = 500) String changeSummary,
        @NotNull @Positive Long expectedDocumentCurrentVersionId,
        @NotNull @PositiveOrZero Integer expectedProjectVersion,
        @NotBlank @Size(max = 100) String idempotencyKey) {}
