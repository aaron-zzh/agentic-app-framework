package com.xuejiai.aaf.module.elder.guard.event;

import java.time.LocalDateTime;

/** 预警进入通知态事件；通知任务监听方只消费标识与必要快照。 */
public record AlertTriggeredEvent(
        Long alertId,
        Long deviceId,
        Long elderId,
        Long ownerUserId,
        String alertType,
        String level,
        String zone,
        LocalDateTime occurredAt,
        boolean late,
        LocalDateTime triggeredAt) {}
