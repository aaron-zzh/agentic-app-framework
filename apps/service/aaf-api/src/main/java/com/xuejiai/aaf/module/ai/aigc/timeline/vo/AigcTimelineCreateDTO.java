package com.xuejiai.aaf.module.ai.aigc.timeline.vo;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record AigcTimelineCreateDTO(
        @NotNull Long projectId,
        @NotNull @PositiveOrZero Integer expectedProjectVersion,
        Long deliverableObjectId,
        @NotBlank @Size(max = 200) String title,
        @PositiveOrZero Long durationMs,
        @Positive BigDecimal frameRate,
        @Positive Integer width,
        @Positive Integer height) {}
