package com.xuejiai.aaf.module.device.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.enums.device.DeviceOnlineStatusEnum;
import com.xuejiai.aaf.common.enums.device.DeviceStatusEnum;
import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.module.device.ErrorCodeConstants;
import com.xuejiai.aaf.module.device.api.DeviceQueryApi;
import com.xuejiai.aaf.module.device.domain.Device;
import com.xuejiai.aaf.module.device.repository.DeviceRepository;

/** 设备只读查询入口实现。 */
@Service
public class DefaultDeviceQueryApi implements DeviceQueryApi {

    private final DeviceRepository deviceRepository;

    public DefaultDeviceQueryApi(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    @Override
    @Transactional
    public DeviceView requireLocked(Long deviceId) {
        return deviceRepository
                .findLockedById(deviceId)
                .map(DefaultDeviceQueryApi::toView)
                .orElseThrow(() -> new BusinessException(ErrorCodeConstants.DEVICE_NOT_FOUND));
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeviceView> listBySubject(String scenarioKey, Long subjectId) {
        return deviceRepository
                .findAllByScenarioKeyAndSubjectIdOrderByIdAsc(scenarioKey, subjectId)
                .stream()
                .map(DefaultDeviceQueryApi::toView)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeviceView> listBoundOnline(String scenarioKey) {
        return deviceRepository
                .findByScenarioKeyAndStatusAndOnlineStatus(
                        scenarioKey,
                        DeviceStatusEnum.BOUND.getCode(),
                        DeviceOnlineStatusEnum.ONLINE.getCode())
                .stream()
                .map(DefaultDeviceQueryApi::toView)
                .toList();
    }

    private static DeviceView toView(Device device) {
        return new DeviceView(
                device.getId(),
                device.getDeviceKey(),
                device.getName(),
                device.getDeviceType(),
                device.getZone(),
                device.getScenarioKey(),
                device.getSubjectId(),
                device.getOwnerUserId(),
                device.getOrgId(),
                device.getWorkspaceId(),
                device.getStatus(),
                device.getOnlineStatus(),
                device.getLastHeartbeatAt(),
                device.getLastPersonSeenAt(),
                device.getCameraStatus(),
                Boolean.TRUE.equals(device.getPrivacyMode()));
    }
}
