package com.xuejiai.aaf.module.ai.aigc.project.vo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** 外部文档引用更新请求。 */
public record AigcProjectDocumentRefUpdateDTO(
        @NotNull @PositiveOrZero Integer expectedProjectVersion) {}
