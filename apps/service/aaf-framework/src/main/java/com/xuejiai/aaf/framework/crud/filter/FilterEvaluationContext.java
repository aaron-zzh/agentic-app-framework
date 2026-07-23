package com.xuejiai.aaf.framework.crud.filter;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;

/** 单次请求内共享的日期筛选求值基准。 */
public record FilterEvaluationContext(Clock clock, ZoneId zoneId, LocalDateTime now) {

    public FilterEvaluationContext {
        clock = Objects.requireNonNull(clock, "clock");
        zoneId = Objects.requireNonNull(zoneId, "zoneId");
        now = Objects.requireNonNull(now, "now");
    }

    public static FilterEvaluationContext create(Clock clock, ZoneId zoneId) {
        Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(zoneId, "zoneId");
        return new FilterEvaluationContext(
                clock, zoneId, LocalDateTime.now(clock.withZone(zoneId)));
    }
}
