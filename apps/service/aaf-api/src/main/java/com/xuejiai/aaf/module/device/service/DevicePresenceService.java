package com.xuejiai.aaf.module.device.service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.enums.device.DeviceCameraStatusEnum;
import com.xuejiai.aaf.common.enums.device.DeviceOnlineStatusEnum;
import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.org.OrgIgnore;
import com.xuejiai.aaf.framework.task.DistributedLock;
import com.xuejiai.aaf.module.device.api.DeviceInboundHandler.DeviceInboundMessage;
import com.xuejiai.aaf.module.device.api.DeviceInboundHandler.InboundType;
import com.xuejiai.aaf.module.device.api.DevicePresenceChangedEvent;
import com.xuejiai.aaf.module.device.repository.DeviceRepository;

/** 设备启动序号与在线状态事实投影。 */
@Service
public class DevicePresenceService {

    private static final Set<String> CAMERA_STATUSES = Set.of(DeviceCameraStatusEnum.ARRAYS);

    private final DeviceRepository deviceRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final DeviceHeartbeatSettings heartbeatSettings;

    public DevicePresenceService(
            DeviceRepository deviceRepository,
            ApplicationEventPublisher eventPublisher,
            DeviceHeartbeatSettings heartbeatSettings) {
        this.deviceRepository = deviceRepository;
        this.eventPublisher = eventPublisher;
        this.heartbeatSettings = heartbeatSettings;
    }

    /** 校验 bootId/statusSequence 并投影状态；旧启动或旧序号返回 false。 */
    @Transactional
    public boolean accept(DeviceInboundMessage inbound) {
        var device = deviceRepository.findLockedById(inbound.deviceId()).orElse(null);
        if (device == null || !accepts(device, inbound)) {
            return false;
        }
        var previous = device.getOnlineStatus();
        var differentBoot = !inbound.bootId().equals(device.getBootId());
        var newerSequence = differentBoot || inbound.statusSequence() > device.getStatusSequence();
        if (!newerSequence) {
            return true;
        }
        if (differentBoot) {
            device.setBootId(inbound.bootId());
            device.setBootStartedAt(
                    LocalDateTime.ofInstant(inbound.deviceOccurredAt(), ZoneOffset.UTC));
        }
        device.setStatusSequence(inbound.statusSequence());
        device.setLastHeartbeatAt(LocalDateTime.now());
        projectPayload(device, inbound);
        deviceRepository.save(device);
        publishChange(device, previous);
        return true;
    }

    private boolean accepts(
            com.xuejiai.aaf.module.device.domain.Device device, DeviceInboundMessage inbound) {
        if (device.getBootId() == null) {
            return true;
        }
        if (inbound.bootId().equals(device.getBootId())) {
            if (inbound.statusSequence() > device.getStatusSequence()) {
                return true;
            }
            return inbound.type() == InboundType.ALERT
                    || inbound.type() == InboundType.ACK
                    || inbound.type() == InboundType.RESULT;
        }
        if (!isOnlinePresence(inbound)) {
            return false;
        }
        var incomingStarted = LocalDateTime.ofInstant(inbound.deviceOccurredAt(), ZoneOffset.UTC);
        return device.getBootStartedAt() == null
                || incomingStarted.isAfter(device.getBootStartedAt());
    }

    private static boolean isOnlinePresence(DeviceInboundMessage inbound) {
        return inbound.type() == InboundType.PRESENCE
                && "ONLINE".equals(inbound.payload().get("status"));
    }

    private static void projectPayload(
            com.xuejiai.aaf.module.device.domain.Device device, DeviceInboundMessage inbound) {
        if (inbound.type() == InboundType.PRESENCE) {
            var status = inbound.payload().get("status");
            device.setOnlineStatus(
                    "OFFLINE".equals(status)
                            ? DeviceOnlineStatusEnum.OFFLINE.getCode()
                            : DeviceOnlineStatusEnum.ONLINE.getCode());
            setText(device::setFirmwareVersion, inbound.payload().get("firmwareVersion"));
        } else {
            device.setOnlineStatus(DeviceOnlineStatusEnum.ONLINE.getCode());
        }
        if (inbound.type() == InboundType.STATUS) {
            var cameraStatus = inbound.payload().get("cameraStatus");
            if (cameraStatus instanceof String value && CAMERA_STATUSES.contains(value)) {
                device.setCameraStatus(value);
            }
            if (inbound.payload().get("privacyMode") instanceof Boolean privacyMode) {
                device.setPrivacyMode(privacyMode);
            }
            if (inbound.payload().get("lastPersonSeenAt") instanceof String timestamp) {
                device.setLastPersonSeenAt(
                        LocalDateTime.ofInstant(
                                java.time.Instant.parse(timestamp), ZoneOffset.UTC));
            }
        }
        if (inbound.type() == InboundType.CAPABILITIES) {
            device.setCapabilities(JsonUtils.toJsonString(inbound.payload()));
        }
    }

    private static void setText(java.util.function.Consumer<String> setter, Object value) {
        if (value instanceof String text && !text.isBlank()) {
            setter.accept(text);
        }
    }

    @OrgIgnore
    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    @DistributedLock(key = "device-presence-scan", ttlSeconds = 25)
    @Transactional
    public void scanHeartbeatTimeouts() {
        var devices =
                deviceRepository.findByOnlineStatusIn(
                        List.of(
                                DeviceOnlineStatusEnum.ONLINE.getCode(),
                                DeviceOnlineStatusEnum.SUSPECT.getCode()));
        var now = LocalDateTime.now();
        var suspectAfter = heartbeatSettings.suspectAfterSeconds();
        var offlineAfter = heartbeatSettings.offlineAfterSeconds();
        for (var device : devices) {
            if (device.getLastHeartbeatAt() == null) {
                continue;
            }
            var ageSeconds =
                    java.time.Duration.between(device.getLastHeartbeatAt(), now).toSeconds();
            var target =
                    ageSeconds >= offlineAfter
                            ? DeviceOnlineStatusEnum.OFFLINE.getCode()
                            : ageSeconds >= suspectAfter
                                    ? DeviceOnlineStatusEnum.SUSPECT.getCode()
                                    : DeviceOnlineStatusEnum.ONLINE.getCode();
            var previous = device.getOnlineStatus();
            if (!target.equals(previous)) {
                device.setOnlineStatus(target);
                deviceRepository.save(device);
                publishChange(device, previous);
            }
        }
    }

    private void publishChange(
            com.xuejiai.aaf.module.device.domain.Device device, String previous) {
        if (device.getOnlineStatus().equals(previous)) {
            return;
        }
        eventPublisher.publishEvent(
                new DevicePresenceChangedEvent(
                        device.getId(),
                        device.getScenarioKey(),
                        device.getSubjectId(),
                        device.getOwnerUserId(),
                        previous,
                        device.getOnlineStatus(),
                        LocalDateTime.now()));
    }
}
