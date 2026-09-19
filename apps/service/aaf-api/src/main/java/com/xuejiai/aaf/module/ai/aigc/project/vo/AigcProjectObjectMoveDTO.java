package com.xuejiai.aaf.module.ai.aigc.project.vo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record AigcProjectObjectMoveDTO(
        Long newParentObjectId,
        Integer orderNo,
        @NotNull @PositiveOrZero Integer expectedProjectVersion,
        @NotNull @PositiveOrZero Long expectedGraphRevision) {}
