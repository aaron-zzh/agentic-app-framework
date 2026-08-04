package com.xuejiai.aaf.module.ai.aigc.work.api.event;

import java.time.Instant;
import java.util.UUID;

public record AigcWorkPublicationChangedEvent(
        UUID eventId,
        Long workId,
        Long projectId,
        Long publicationId,
        String status,
        Instant occurredAt) {}
