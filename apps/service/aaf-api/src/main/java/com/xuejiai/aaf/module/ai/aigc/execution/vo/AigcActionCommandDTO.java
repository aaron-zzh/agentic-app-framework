package com.xuejiai.aaf.module.ai.aigc.execution.vo;

import java.util.List;

import jakarta.validation.constraints.NotBlank;

public record AigcActionCommandDTO(
        Long objectId,
        @NotBlank String actionKey,
        String prompt,
        List<Long> attachmentMediaVersionIds,
        Boolean confirmed,
        @NotBlank String idempotencyKey) {

    public AigcActionCommandDTO {
        attachmentMediaVersionIds =
                attachmentMediaVersionIds == null
                        ? List.of()
                        : List.copyOf(attachmentMediaVersionIds);
    }
}
