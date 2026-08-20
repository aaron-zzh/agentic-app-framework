package com.xuejiai.aaf.module.ai.assistant.vo;

import java.util.Map;

import com.xuejiai.aaf.framework.intelligent.assistant.model.ExecutionInput;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DelegatedTaskInputDTO(
        @jakarta.validation.constraints.NotBlank @Size(max = 128) String inputId,
        @NotNull ExecutionInput.Kind kind,
        @NotNull @Size(max = 64) Map<@Size(max = 128) String, @Size(max = 4000) String> values) {}
