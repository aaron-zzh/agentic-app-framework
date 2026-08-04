package com.xuejiai.aaf.module.ai.aigc.work.api.event;

import java.time.Instant;
import java.util.UUID;

public record AigcWorkCollectedEvent(
        UUID eventId,
        Long workId,
        Long projectId,
        Long deliverableObjectId,
        Long adoptedObjectVersionId,
        Instant occurredAt) {}
