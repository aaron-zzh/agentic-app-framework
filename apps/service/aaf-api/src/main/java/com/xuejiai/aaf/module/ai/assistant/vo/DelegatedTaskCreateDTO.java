package com.xuejiai.aaf.module.ai.assistant.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DelegatedTaskCreateDTO(
        @NotNull Source source,
        @NotBlank String conversationId,
        @NotBlank String title,
        String description,
        int priority) {

    public enum Source {
        CONVERSATION,
        MANUAL
    }
}
