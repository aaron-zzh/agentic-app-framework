package com.xuejiai.aaf.module.device.api;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 设备模块解析后的实时会话上下文，只含受控业务变量与身份边界。 */
public record RealtimeDeviceContext(
        Long deviceId,
        String scenarioKey,
        Long subjectId,
        Long ownerUserId,
        Long orgId,
        Long workspaceId,
        String requestedRoleKey,
        String scenarioDefaultRoleKey,
        Set<String> allowedRoleKeys,
        Map<String, Object> chatVariables) {

    public RealtimeDeviceContext {
        Objects.requireNonNull(deviceId, "deviceId 不能为空");
        Objects.requireNonNull(scenarioKey, "scenarioKey 不能为空");
        Objects.requireNonNull(ownerUserId, "ownerUserId 不能为空");
        Objects.requireNonNull(orgId, "orgId 不能为空");
        Objects.requireNonNull(workspaceId, "workspaceId 不能为空");
        allowedRoleKeys =
                Set.copyOf(Objects.requireNonNull(allowedRoleKeys, "allowedRoleKeys 不能为空"));
        chatVariables = Map.copyOf(Objects.requireNonNull(chatVariables, "chatVariables 不能为空"));
    }
}
