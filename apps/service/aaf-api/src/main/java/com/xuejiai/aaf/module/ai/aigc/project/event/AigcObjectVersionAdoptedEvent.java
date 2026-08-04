package com.xuejiai.aaf.module.ai.aigc.project.event;

import java.time.Instant;
import java.util.UUID;

public record AigcObjectVersionAdoptedEvent(
        UUID eventId,
        Long projectId,
        Long projectObjectId,
        Long objectVersionId,
        Long projectRevisionNo,
        Instant occurredAt) {}
