package com.xuejiai.aaf.module.ai.aigc.execution.vo;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;

public record AigcActionCommandDTO(
        Long objectId,
        @NotBlank String actionKey,
        String prompt,
        String requestedModelId,
        Map<String, Object> actionArguments,
        List<Long> attachmentMediaVersionIds,
        List<Long> selectedProjectObjectIds,
        Long expectedGraphRevision,
        Boolean confirmed,
        @NotBlank String idempotencyKey) {

    public AigcActionCommandDTO {
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
}
