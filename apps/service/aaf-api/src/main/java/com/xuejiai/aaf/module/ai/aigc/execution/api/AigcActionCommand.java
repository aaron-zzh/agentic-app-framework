package com.xuejiai.aaf.module.ai.aigc.execution.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record AigcActionCommand(
        Long projectId,
        Long projectObjectId,
        String actionKey,
        String prompt,
        String requestedModelId,
        Map<String, Object> actionArguments,
        List<Long> attachmentMediaVersionIds,
        List<Long> selectedProjectObjectIds,
        Long expectedGraphRevision,
        boolean confirmed,
        String idempotencyKey) {

    public AigcActionCommand {
        Objects.requireNonNull(projectId, "projectId 不能为空");
        actionKey = requireText(actionKey, "actionKey");
        idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
        requestedModelId = optionalText(requestedModelId, "requestedModelId");
        actionArguments =
                actionArguments == null
                        ? Map.of()
                        : Collections.unmodifiableMap(new LinkedHashMap<>(actionArguments));
        attachmentMediaVersionIds =
                attachmentMediaVersionIds == null
                        ? List.of()
                        : List.copyOf(attachmentMediaVersionIds);
        selectedProjectObjectIds =
                selectedProjectObjectIds == null
                        ? List.of()
                        : List.copyOf(selectedProjectObjectIds);
    }

    private static String optionalText(String value, String field) {
        if (value == null) {
            return null;
        }
        return requireText(value, field);
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " 不能为空");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空白");
        }
        return value;
    }
}
