package com.xuejiai.aaf.module.ai.aigc.work.api;

import java.time.Instant;

public record AigcPublicationRetryCommand(
        Long workId,
        Long failedPublicationId,
        Integer expectedProjectVersion,
        Integer expectedWorkVersion,
        Integer expectedPublicationVersion,
        Instant scheduledAt,
        String idempotencyKey) {}
