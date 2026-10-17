package com.xuejiai.aaf.module.device.security;

import java.security.Principal;

/** 设备认证主体，仅携带设备身份、计费与租户上下文，不继承所有者角色。 */
public record DevicePrincipal(
        Long deviceId,
        String deviceKey,
        Long ownerUserId,
        String scenarioKey,
        Long subjectId,
        String roleKey,
        Long orgId,
        Long workspaceId)
        implements Principal {

    @Override
    public String getName() {
        return deviceKey;
    }
}
