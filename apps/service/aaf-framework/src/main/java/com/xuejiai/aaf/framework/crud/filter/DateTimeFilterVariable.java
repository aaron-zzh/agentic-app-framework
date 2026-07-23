package com.xuejiai.aaf.framework.crud.filter;

import java.time.LocalDateTime;
import java.util.Map;

/** 允许在日期筛选中使用的相对时间变量。 */
public enum DateTimeFilterVariable {
    NOW("$now"),
    TODAY_START("$todayStart"),
    TOMORROW_START("$tomorrowStart"),
    NOW_PLUS_3_DAYS("$nowPlus3Days");

    private static final Map<String, DateTimeFilterVariable> BY_TOKEN =
            Map.ofEntries(
                    Map.entry(NOW.token, NOW),
                    Map.entry(TODAY_START.token, TODAY_START),
                    Map.entry(TOMORROW_START.token, TOMORROW_START),
                    Map.entry(NOW_PLUS_3_DAYS.token, NOW_PLUS_3_DAYS));

    private final String token;

    DateTimeFilterVariable(String token) {
        this.token = token;
    }

    public String token() {
        return token;
    }

    static DateTimeFilterVariable fromToken(String value) {
        return BY_TOKEN.get(value);
    }

    LocalDateTime resolve(LocalDateTime now) {
        return switch (this) {
            case NOW -> now;
            case TODAY_START -> now.toLocalDate().atStartOfDay();
            case TOMORROW_START -> now.toLocalDate().plusDays(1).atStartOfDay();
            case NOW_PLUS_3_DAYS -> now.plusDays(3);
        };
    }
}
