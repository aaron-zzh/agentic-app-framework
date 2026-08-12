package com.xuejiai.aaf.module.ai.aigc.project.vo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** 项目文档关联请求。 */
public record AigcProjectDocumentRefDTO(
        Long objectId,
        @NotNull @Positive Long documentVersionId,
        @Size(max = 32) String role,
        @PositiveOrZero Integer sortOrder,
        @NotNull @PositiveOrZero Integer expectedProjectVersion) {}
