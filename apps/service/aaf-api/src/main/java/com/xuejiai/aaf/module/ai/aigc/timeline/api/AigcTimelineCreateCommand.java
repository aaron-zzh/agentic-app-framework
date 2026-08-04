package com.xuejiai.aaf.module.ai.aigc.timeline.api;

import java.math.BigDecimal;

public record AigcTimelineCreateCommand(
        Long projectId,
        Long deliverableObjectId,
        String title,
        Long durationMs,
        BigDecimal frameRate,
        Integer width,
        Integer height) {}
