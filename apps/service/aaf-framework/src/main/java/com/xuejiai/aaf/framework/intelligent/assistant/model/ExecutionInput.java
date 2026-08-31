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
 * <p><b>{@code kind} 是客户端建议值，不直接信任</b>——服务端须经 {@code InputClassifier} 重新判定后才生效 （方案 C，2026-08-30
 * 拍板）。{@code text} 承载原始自然语言输入，MODIFY 场景下作为重新协调规划的目标描述来源； CANCEL 不再作为本入口的合法输出值，取消统一走既有确定性 {@code
 * /stop} 端点。
 */
public record ExecutionInput(
        String inputId,
        TenantId tenantId,
        UserId userId,
        TaskId taskId,
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

    /** 客户端可建议的输入分类；不含 {@code CANCEL}——取消统一走确定性 {@code /stop} 端点，不经本入口分类判定。 */
    public enum Kind {
        MODIFY,
        SUPPLEMENT,
        UNRELATED
    }
}
