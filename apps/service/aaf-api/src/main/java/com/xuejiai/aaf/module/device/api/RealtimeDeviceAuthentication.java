package com.xuejiai.aaf.module.device.api;

import java.util.Objects;

/** WebSocket 握手已认证的设备最小身份快照，不含任何凭据。 */
public record RealtimeDeviceAuthentication(
        Long deviceId,
        String deviceKey,
        Long ownerUserId,
        String scenarioKey,
        Long subjectId,
        String roleKey,
        Long orgId,
        Long workspaceId) {

    public RealtimeDeviceAuthentication {
        Objects.requireNonNull(deviceId, "deviceId 不能为空");
        Objects.requireNonNull(deviceKey, "deviceKey 不能为空");
        Objects.requireNonNull(ownerUserId, "ownerUserId 不能为空");
        Objects.requireNonNull(scenarioKey, "scenarioKey 不能为空");
        Objects.requireNonNull(orgId, "orgId 不能为空");
        Objects.requireNonNull(workspaceId, "workspaceId 不能为空");
    }
}
