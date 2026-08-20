package com.xuejiai.aaf.framework.intelligent.assistant.model;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;

/** 委托任务的结构化外部输入；MODIFY/SUPPLEMENT 仅承载澄清字段值。 */
public record ExecutionInput(
        String inputId,
        TenantId tenantId,
        UserId userId,
        TaskId taskId,
        Kind kind,
        Map<String, String> values,
        Instant receivedAt) {

    public ExecutionInput {
        if (inputId == null || inputId.isBlank()) {
            throw new IllegalArgumentException("inputId 不能为空白");
        }
        Objects.requireNonNull(tenantId, "tenantId 不能为空");
        Objects.requireNonNull(userId, "userId 不能为空");
        Objects.requireNonNull(taskId, "taskId 不能为空");
        Objects.requireNonNull(kind, "kind 不能为空");
        Objects.requireNonNull(receivedAt, "receivedAt 不能为空");
        var normalized = new LinkedHashMap<String, String>();
        Objects.requireNonNull(values, "values 不能为空")
                .forEach(
                        (field, value) -> {
                            if (field == null
                                    || field.isBlank()
                                    || value == null
                                    || value.isBlank()) {
                                throw new IllegalArgumentException("输入字段和值不能为空白");
                            }
                            normalized.put(field.trim(), value.trim());
                        });
        values = Map.copyOf(normalized);
        if ((kind == Kind.MODIFY || kind == Kind.SUPPLEMENT) && values.isEmpty()) {
            throw new IllegalArgumentException(kind + " 必须携带至少一个澄清字段值");
        }
        if ((kind == Kind.CANCEL || kind == Kind.UNRELATED) && !values.isEmpty()) {
            throw new IllegalArgumentException(kind + " 禁止携带澄清字段值");
        }
    }

    public enum Kind {
        CANCEL,
        MODIFY,
        SUPPLEMENT,
        UNRELATED
    }
}
