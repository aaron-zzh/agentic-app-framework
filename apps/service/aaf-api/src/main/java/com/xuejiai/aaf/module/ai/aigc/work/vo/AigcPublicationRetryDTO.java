package com.xuejiai.aaf.module.ai.aigc.work.vo;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AigcPublicationRetryDTO(
        @NotNull Integer expectedProjectVersion,
        @NotNull Integer expectedWorkVersion,
        @NotNull Integer expectedPublicationVersion,
        Instant scheduledAt,
        @NotBlank String idempotencyKey) {}
