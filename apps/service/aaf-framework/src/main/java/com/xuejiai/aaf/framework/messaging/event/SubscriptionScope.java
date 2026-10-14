package com.xuejiai.aaf.framework.messaging.event;

import java.util.Objects;

/**
 * 事件订阅范围：连接所属用户与其授权的组织、工作区。
 *
 * <p>订阅方收到事件当且仅当 owner 相同，且事件为用户级（组织与工作区都为空）或范围精确匹配（null 只匹配 null）。
 */
public record SubscriptionScope(Long ownerId, Long orgId, Long workspaceId) {

    public SubscriptionScope {
        Objects.requireNonNull(ownerId, "ownerId 不能为空");
    }
}
