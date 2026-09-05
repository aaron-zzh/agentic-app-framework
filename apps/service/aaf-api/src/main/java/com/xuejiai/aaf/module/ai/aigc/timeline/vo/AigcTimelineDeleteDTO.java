package com.xuejiai.aaf.module.ai.aigc.timeline.vo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record AigcTimelineDeleteDTO(
        @NotNull Long projectId,
        @NotNull @PositiveOrZero Integer expectedProjectVersion,
        @NotNull @PositiveOrZero Integer expectedVersion) {}
