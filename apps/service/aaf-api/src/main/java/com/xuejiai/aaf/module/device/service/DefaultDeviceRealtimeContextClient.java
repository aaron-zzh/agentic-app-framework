package com.xuejiai.aaf.module.device.service;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;
import static com.xuejiai.aaf.module.device.ErrorCodeConstants.DEVICE_NOT_FOUND;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.enums.device.DeviceStatusEnum;
import com.xuejiai.aaf.framework.org.OrgContext;
import com.xuejiai.aaf.module.device.api.DeviceRealtimeContextClient;
import com.xuejiai.aaf.module.device.api.RealtimeDeviceAuthentication;
import com.xuejiai.aaf.module.device.api.RealtimeDeviceContext;
import com.xuejiai.aaf.module.device.domain.Device;
import com.xuejiai.aaf.module.device.repository.DeviceRepository;
import com.xuejiai.aaf.module.device.scenario.DeviceBindingContext;
import com.xuejiai.aaf.module.device.scenario.DeviceScenarioAccess;
import com.xuejiai.aaf.module.device.security.DevicePrincipal;

/** 在 device 模块内复核实时会话设备的当前绑定与场景上下文。 */
@Service
public class DefaultDeviceRealtimeContextClient implements DeviceRealtimeContextClient {

    private final DeviceRepository deviceRepository;
    private final DeviceScenarioAccess scenarioAccess;
    private final DeviceOwnerExecutionService ownerExecutionService;

    public DefaultDeviceRealtimeContextClient(
            DeviceRepository deviceRepository,
            DeviceScenarioAccess scenarioAccess,
            DeviceOwnerExecutionService ownerExecutionService) {
        this.deviceRepository = deviceRepository;
        this.scenarioAccess = scenarioAccess;
        this.ownerExecutionService = ownerExecutionService;
    }

    @Override
    @Transactional(readOnly = true)
    public RealtimeDeviceContext resolve(RealtimeDeviceAuthentication authentication) {
        var device =
                deviceRepository
                        .findByDeviceKey(authentication.deviceKey())
                        .filter(item -> item.getId().equals(authentication.deviceId()))
                        .filter(item -> DeviceStatusEnum.BOUND.getCode().equals(item.getStatus()))
                        .filter(item -> item.getCredentialRevokedAt() == null)
                        .filter(item -> sameBinding(item, authentication))
                        .orElseThrow(() -> exception(DEVICE_NOT_FOUND));
        var scenario = scenarioAccess.requireEnabled(device.getScenarioKey());
        var principal = principal(device);
        ownerExecutionService.runAsOwner(
                principal,
                "realtime-device-binding",
                () -> {
                    OrgContext.runIfAbsent(
                            principal.orgId(),
                            principal.workspaceId(),
                            () -> {
                                scenario.validateBinding(
                                        new DeviceBindingContext(
                                                principal.ownerUserId(),
                                                principal.orgId(),
                                                principal.workspaceId(),
                                                device.getSubjectType(),
                                                principal.subjectId()));
                                return null;
                            });
                    return null;
                });
        var variables =
                ownerExecutionService.runAsOwner(
                        principal,
                        "realtime-device-context",
                        () ->
                                OrgContext.runIfAbsent(
                                        principal.orgId(),
                                        principal.workspaceId(),
                                        () -> scenario.chatVariables(principal)));
        return new RealtimeDeviceContext(
                device.getId(),
                scenario.key(),
                device.getSubjectId(),
                device.getOwnerUserId(),
                device.getOrgId(),
                device.getWorkspaceId(),
                device.getRoleKey(),
                scenario.defaultRoleKey(),
                scenario.allowedRoleKeys(),
                variables);
    }

    @Override
    @Transactional(readOnly = true)
    public AutoCloseable openToolScope(RealtimeDeviceContext context) {
        var device =
                deviceRepository
                        .findById(context.deviceId())
                        .filter(item -> DeviceStatusEnum.BOUND.getCode().equals(item.getStatus()))
                        .filter(item -> item.getCredentialRevokedAt() == null)
                        .orElseThrow(() -> exception(DEVICE_NOT_FOUND));
        var scenario = scenarioAccess.requireEnabled(context.scenarioKey());
        var principal = principal(device);
        return ownerExecutionService.runAsOwner(
                principal,
                "realtime-device-tools",
                () ->
                        OrgContext.runIfAbsent(
                                principal.orgId(),
                                principal.workspaceId(),
                                () -> scenario.openToolScope(principal)));
    }

    private static boolean sameBinding(Device device, RealtimeDeviceAuthentication authentication) {
        return device.getOwnerUserId().equals(authentication.ownerUserId())
                && device.getScenarioKey().equals(authentication.scenarioKey())
                && java.util.Objects.equals(device.getSubjectId(), authentication.subjectId())
                && java.util.Objects.equals(device.getRoleKey(), authentication.roleKey())
                && device.getOrgId().equals(authentication.orgId())
                && device.getWorkspaceId().equals(authentication.workspaceId());
    }

    private static DevicePrincipal principal(Device device) {
        return new DevicePrincipal(
                device.getId(),
                device.getDeviceKey(),
                device.getOwnerUserId(),
                device.getScenarioKey(),
                device.getSubjectId(),
                device.getRoleKey(),
                device.getOrgId(),
                device.getWorkspaceId());
    }
}
