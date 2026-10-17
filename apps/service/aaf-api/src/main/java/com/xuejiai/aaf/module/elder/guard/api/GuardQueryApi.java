package com.xuejiai.aaf.module.elder.guard.api;

import java.time.LocalDateTime;
import java.util.List;

/** 安全守护只读契约；调用方只取得脱敏的预警与设备状态快照。 */
public interface GuardQueryApi {

    List<AlertSnapshot> listRecentAlerts(Long elderId, int limit);

    List<DeviceSnapshot> listDevices(Long elderId);

    record AlertSnapshot(
            Long id,
            String alertType,
            String level,
            String status,
            String zone,
            LocalDateTime occurredAt,
            boolean late,
            LocalDateTime claimedAt,
            LocalDateTime acknowledgedAt,
            LocalDateTime resolvedAt) {}

    record DeviceSnapshot(
            Long id,
            String name,
            String deviceType,
            String zone,
            String status,
            String onlineStatus,
            LocalDateTime lastHeartbeatAt,
            String cameraStatus,
            boolean privacyMode) {}
}
