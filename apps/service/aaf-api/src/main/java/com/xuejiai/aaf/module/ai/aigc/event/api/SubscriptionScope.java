package com.xuejiai.aaf.module.ai.aigc.event.api;

import java.util.Objects;

/** Activity replay 与 live broadcast 共用的精确租户作用域。 */
public record SubscriptionScope(Long ownerId, Long orgId, Long workspaceId) {

    public SubscriptionScope {
        Objects.requireNonNull(ownerId, "ownerId 不能为空");
    }
}
