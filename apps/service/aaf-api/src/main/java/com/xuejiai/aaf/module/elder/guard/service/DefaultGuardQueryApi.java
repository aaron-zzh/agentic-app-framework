package com.xuejiai.aaf.module.elder.guard.service;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.enums.elder.AuthorizationScopeEnum;
import com.xuejiai.aaf.module.device.api.DeviceQueryApi;
import com.xuejiai.aaf.module.elder.guard.api.GuardQueryApi;
import com.xuejiai.aaf.module.elder.guard.repository.AlertEventRepository;
import com.xuejiai.aaf.module.elder.profile.api.ElderQueryApi;
import com.xuejiai.aaf.module.elder.scenario.ElderDeviceScenario;

import lombok.RequiredArgsConstructor;

/** guard 对智能层开放的只读查询实现，授权判断始终委托 profile 的 ReBAC 契约。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DefaultGuardQueryApi implements GuardQueryApi {

    private static final int MAX_ALERT_LIMIT = 20;

    private final AlertEventRepository alertRepository;
    private final DeviceQueryApi deviceQueryApi;
    private final ElderQueryApi elderQueryApi;

    @Override
    public List<AlertSnapshot> listRecentAlerts(Long elderId, int limit) {
        elderQueryApi.requireAccess(elderId, AuthorizationScopeEnum.VIEW_ALERT.getCode());
        if (limit <= 0) {
            throw new IllegalArgumentException("预警查询条数必须大于零");
        }
        return alertRepository
                .findByElderIdOrderByDeviceOccurredAtDescIdDesc(
                        elderId, PageRequest.of(0, Math.min(limit, MAX_ALERT_LIMIT)))
                .stream()
                .map(
                        alert ->
                                new AlertSnapshot(
                                        alert.getId(),
                                        alert.getAlertType(),
                                        alert.getLevel(),
                                        alert.getStatus(),
                                        alert.getZone(),
                                        alert.getDeviceOccurredAt(),
                                        Boolean.TRUE.equals(alert.getLate()),
                                        alert.getClaimedAt(),
                                        alert.getAcknowledgedAt(),
                                        alert.getResolvedAt()))
                .toList();
    }

    @Override
    public List<DeviceSnapshot> listDevices(Long elderId) {
        elderQueryApi.requireAccess(elderId, AuthorizationScopeEnum.VIEW_ALERT.getCode());
        return deviceQueryApi.listBySubject(ElderDeviceScenario.KEY, elderId).stream()
                .map(
                        device ->
                                new DeviceSnapshot(
                                        device.id(),
                                        device.name(),
                                        device.deviceType(),
                                        device.zone(),
                                        device.status(),
                                        device.onlineStatus(),
                                        device.lastHeartbeatAt(),
                                        device.cameraStatus(),
                                        device.privacyMode()))
                .toList();
    }
}
