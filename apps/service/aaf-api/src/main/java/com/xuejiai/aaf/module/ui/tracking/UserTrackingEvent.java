package com.xuejiai.aaf.module.ui.tracking;

import java.time.LocalDateTime;

/**
 * 用户行为追踪事件（内存存储模型）。
 */
public record UserTrackingEvent(
        String type,
        String page,
        String target,
        Integer x,
        Integer y,
        Long timestamp,
        String extra,
        LocalDateTime receivedAt
) {}
