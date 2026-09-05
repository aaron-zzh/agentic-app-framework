package com.xuejiai.aaf.module.ai.aigc.project.event;

import java.time.Instant;
import java.util.UUID;

public record AigcProjectReviewApprovedEvent(
        UUID eventId,
        Long projectId,
        Long reviewObjectId,
        Long deliverableSetObjectId,
        Long manifestObjectVersionId,
        Instant occurredAt) {}
