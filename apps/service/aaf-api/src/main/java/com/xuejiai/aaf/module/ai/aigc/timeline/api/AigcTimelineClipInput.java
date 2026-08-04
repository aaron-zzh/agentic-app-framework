package com.xuejiai.aaf.module.ai.aigc.timeline.api;

import java.math.BigDecimal;

public record AigcTimelineClipInput(
        Long mediaVersionId,
        Long sourceObjectId,
        Long sourceObjectVersionId,
        Long positionMs,
        Long inMs,
        Long outMs,
        String propertiesJson,
        String transitionJson,
        BigDecimal volume) {}
