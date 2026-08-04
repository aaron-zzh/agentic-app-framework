package com.xuejiai.aaf.module.ai.aigc.execution.api;

import java.util.List;
import java.util.Objects;

public record AigcActionCommand(
        Long projectId,
        Long projectObjectId,
        String actionKey,
        String prompt,
        List<Long> attachmentMediaVersionIds,
        boolean confirmed,
        String idempotencyKey) {

    public AigcActionCommand {
        Objects.requireNonNull(projectId, "projectId 不能为空");
        actionKey = requireText(actionKey, "actionKey");
        idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
        attachmentMediaVersionIds =
                attachmentMediaVersionIds == null
                        ? List.of()
                        : List.copyOf(attachmentMediaVersionIds);
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " 不能为空");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空白");
        }
        return value;
    }
}
