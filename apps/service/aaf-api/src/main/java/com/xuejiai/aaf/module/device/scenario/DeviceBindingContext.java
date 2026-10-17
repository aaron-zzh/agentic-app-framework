package com.xuejiai.aaf.module.device.scenario;

/** 设备绑定时传给场景的可信上下文。 */
public record DeviceBindingContext(
        Long ownerUserId, Long orgId, Long workspaceId, String subjectType, Long subjectId) {}
