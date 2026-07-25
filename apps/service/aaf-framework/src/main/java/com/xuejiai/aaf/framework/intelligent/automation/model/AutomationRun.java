package com.xuejiai.aaf.framework.intelligent.automation.model;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;

/** 单次触发绑定定义版本快照，后续模板升级不改变运行中实例。 */
public record AutomationRun(
        TenantId tenantId,
        String runId,
        String automationId,
        long definitionVersion,
        String triggerKey,
        TaskId delegatedTaskId,
        AutomationDefinition definitionSnapshot,
        Map<String, Object> parameters,
        Status status,
        String failure,
        Instant createdAt,
        Instant updatedAt) {
    public AutomationRun {
        Objects.requireNonNull(tenantId, "tenantId 不能为空");
        require(runId, "runId");
        require(automationId, "automationId");
        require(triggerKey, "triggerKey");
        Objects.requireNonNull(delegatedTaskId, "delegatedTaskId 不能为空");
        Objects.requireNonNull(definitionSnapshot, "definitionSnapshot 不能为空");
        parameters = Map.copyOf(Objects.requireNonNull(parameters, "parameters 不能为空"));
        Objects.requireNonNull(status, "status 不能为空");
        Objects.requireNonNull(createdAt, "createdAt 不能为空");
        Objects.requireNonNull(updatedAt, "updatedAt 不能为空");
        if (definitionVersion != definitionSnapshot.version()) throw new IllegalArgumentException("运行版本快照不一致");
    }
    public enum Status { PENDING, DISPATCHED, COMPLETED, FAILED, CANCELED }
    private static void require(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " 不能为空白");
    }
}
