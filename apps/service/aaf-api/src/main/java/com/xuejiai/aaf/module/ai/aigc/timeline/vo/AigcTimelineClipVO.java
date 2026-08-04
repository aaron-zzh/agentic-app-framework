package com.xuejiai.aaf.module.ai.aigc.timeline.vo;

import java.math.BigDecimal;
import java.util.Map;

public record AigcTimelineClipVO(
        Long id,
        Long trackId,
        Long mediaVersionId,
        Long sourceObjectId,
        Long sourceObjectVersionId,
        Long positionMs,
        Long inMs,
        Long outMs,
        Map<String, Object> properties,
        Map<String, Object> transition,
        BigDecimal volume) {}
