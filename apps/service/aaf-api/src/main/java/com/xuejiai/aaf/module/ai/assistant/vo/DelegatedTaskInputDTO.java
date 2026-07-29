package com.xuejiai.aaf.module.ai.assistant.vo;

import com.xuejiai.aaf.framework.intelligent.assistant.model.ExecutionInput;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DelegatedTaskInputDTO(
        @NotBlank @Size(max = 128) String inputId,
        @NotNull ExecutionInput.Kind kind,
        @Size(max = 20000) String content) {}
