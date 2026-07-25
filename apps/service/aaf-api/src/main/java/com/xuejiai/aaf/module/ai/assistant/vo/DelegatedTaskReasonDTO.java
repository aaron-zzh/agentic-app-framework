package com.xuejiai.aaf.module.ai.assistant.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DelegatedTaskReasonDTO(
        @NotBlank @Size(max = 500) String reason) {}
