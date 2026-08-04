package com.xuejiai.aaf.module.ai.aigc.timeline.vo;

import java.math.BigDecimal;

import jakarta.validation.constraints.PositiveOrZero;

public record AigcTimelineClipDTO(
        Long mediaVersionId,
        Long sourceObjectId,
        Long sourceObjectVersionId,
        @PositiveOrZero Long positionMs,
        @PositiveOrZero Long inMs,
        @PositiveOrZero Long outMs,
        String propertiesJson,
        String transitionJson,
        @PositiveOrZero BigDecimal volume) {}
