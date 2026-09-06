package com.xuejiai.aaf.module.ai.aigc.project.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AigcProjectLifecycleDTO(
        @NotNull Integer expectedProjectVersion, @NotBlank String idempotencyKey, String reason) {}
