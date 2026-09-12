package com.xuejiai.aaf.framework.intelligent.shared.event;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.EventId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;

/** canonical execution transition producer 的稳定事件标识。 */
public final class CanonicalExecutionEventId {

    private CanonicalExecutionEventId() {}

    public static EventId of(TenantId tenantId, ExecutionId executionId, String producerKey) {
        Objects.requireNonNull(tenantId, "tenantId 不能为空");
        Objects.requireNonNull(executionId, "executionId 不能为空");
        if (producerKey == null || producerKey.isBlank()) {
            throw new IllegalArgumentException("producerKey 不能为空白");
        }
        var canonical = tenantId.value() + '|' + executionId.value() + '|' + producerKey.trim();
        return new EventId(
                "event:" + UUID.nameUUIDFromBytes(canonical.getBytes(StandardCharsets.UTF_8)));
    }
}
