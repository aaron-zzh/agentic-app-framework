package com.xuejiai.aaf.module.ai.aigc.work.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AigcWorkArchiveDTO(
        @NotNull Integer expectedProjectVersion,
        @NotNull Integer expectedWorkVersion,
        @NotBlank String idempotencyKey,
        @NotBlank String reason) {}
