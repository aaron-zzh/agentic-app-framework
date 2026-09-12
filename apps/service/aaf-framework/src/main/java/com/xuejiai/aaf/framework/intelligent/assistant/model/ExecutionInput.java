package com.xuejiai.aaf.framework.intelligent.assistant.model;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;

/**
 * 委托任务的结构化外部输入。
 *
 * <p>{@code kind} 由服务端具体 canonical API 决定：结构化澄清入口只产生 {@code SUPPLEMENT}，修改与无关输入由各自显式命令产生； 不通过模型
 * classifier 猜测或回退。{@code text} 承载原始自然语言输入，MODIFY 场景下作为重新协调规划的目标描述来源； CANCEL
 * 不再作为本入口的合法输出值，取消统一走既有确定性 {@code /stop} 端点。
 */
public record ExecutionInput(
        String inputId,
        TenantId tenantId,
        UserId userId,
        TaskId taskId,
        String requestId,
        Kind kind,
        String text,
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
        requestId = requestId == null || requestId.isBlank() ? null : requestId.trim();
        if ((kind == Kind.SUPPLEMENT) != (requestId != null)) {
            throw new IllegalArgumentException("仅 SUPPLEMENT 必须且只能携带 requestId");
        }
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
        if (kind == Kind.SUPPLEMENT && values.isEmpty()) {
            throw new IllegalArgumentException(kind + " 必须携带至少一个澄清字段值");
        }
        if (kind == Kind.UNRELATED && !values.isEmpty()) {
            throw new IllegalArgumentException(kind + " 禁止携带澄清字段值");
        }
        if (kind == Kind.MODIFY && (text == null || text.isBlank()) && values.isEmpty()) {
            throw new IllegalArgumentException("MODIFY 必须携带原始文本或澄清字段值之一");
        }
        text = text == null ? null : text.trim();
        if (text != null && text.isBlank()) {
            text = null;
        }
    }

    /** canonical 输入命令类型；不含 {@code CANCEL}，取消统一走确定性 {@code /stop} 端点。 */
    public enum Kind {
        MODIFY,
        SUPPLEMENT,
        UNRELATED
    }
}
