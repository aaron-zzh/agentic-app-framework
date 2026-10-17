package com.xuejiai.aaf.module.device.api;

import java.time.LocalDateTime;

/** 设备在线状态变化事件。监听方只依赖标识与状态快照。 */
public record DevicePresenceChangedEvent(
        Long deviceId,
        String scenarioKey,
        Long subjectId,
        Long ownerUserId,
        String previousStatus,
        String currentStatus,
        LocalDateTime changedAt) {}
