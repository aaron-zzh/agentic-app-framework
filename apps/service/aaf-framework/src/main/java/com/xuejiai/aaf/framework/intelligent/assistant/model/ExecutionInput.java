package com.xuejiai.aaf.framework.intelligent.assistant.model;

import java.time.Instant;
import java.util.Objects;

import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;

/** 运行期输入的四种确定语义。 */
public record ExecutionInput(
        String inputId,
        TenantId tenantId,
        UserId userId,
        TaskId taskId,
        Kind kind,
        String content,
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
        if (kind != Kind.CANCEL && (content == null || content.isBlank())) {
            throw new IllegalArgumentException(kind + " 输入内容不能为空白");
        }
        content = content == null ? "" : content;
    }

    public enum Kind {
        CANCEL,
        MODIFY,
        SUPPLEMENT,
        UNRELATED
    }
}
