package com.xuejiai.aaf.module.device.service;

import java.util.Objects;
import java.util.function.Supplier;

import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.security.PermissionExecutionService;
import com.xuejiai.aaf.module.device.security.DevicePrincipal;

/** 将设备发起的计费或助理调用限制在绑定所有者的权限上下文内。 */
@Service
public class DeviceOwnerExecutionService {

    private final PermissionExecutionService permissionExecutionService;

    public DeviceOwnerExecutionService(PermissionExecutionService permissionExecutionService) {
        this.permissionExecutionService = permissionExecutionService;
    }

    public <T> T runAsOwner(DevicePrincipal principal, String reason, Supplier<T> supplier) {
        var device = Objects.requireNonNull(principal, "principal");
        return permissionExecutionService.runAsOwner(
                device.ownerUserId(), auditReason(device, reason), supplier);
    }

    public void runAsOwner(DevicePrincipal principal, String reason, Runnable runnable) {
        var device = Objects.requireNonNull(principal, "principal");
        permissionExecutionService.runAsOwner(
                device.ownerUserId(), auditReason(device, reason), runnable);
    }

    private static String auditReason(DevicePrincipal principal, String reason) {
        return "device:%d:%s"
                .formatted(
                        principal.deviceId(),
                        reason == null || reason.isBlank() ? "unspecified" : reason);
    }
}
