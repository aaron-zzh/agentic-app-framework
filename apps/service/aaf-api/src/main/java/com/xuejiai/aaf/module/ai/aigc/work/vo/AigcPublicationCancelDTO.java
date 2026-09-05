package com.xuejiai.aaf.module.ai.aigc.work.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AigcPublicationCancelDTO(
        @NotNull Integer expectedProjectVersion,
        @NotNull Integer expectedWorkVersion,
        @NotNull Integer expectedPublicationVersion,
        @NotBlank String reason,
        @NotBlank String idempotencyKey) {}
