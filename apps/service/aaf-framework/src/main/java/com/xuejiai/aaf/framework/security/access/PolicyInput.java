package com.xuejiai.aaf.framework.security.access;

import java.util.Map;

/** ABAC 策略输入上下文。 */
public record PolicyInput(
        Long operatorId,
        Long ownerId,
        String action,
        String objectType,
        String objectId,
        Map<String, Object> attributes) {}
