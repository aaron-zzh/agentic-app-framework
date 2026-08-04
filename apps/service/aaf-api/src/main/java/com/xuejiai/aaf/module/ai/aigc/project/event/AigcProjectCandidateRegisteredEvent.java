package com.xuejiai.aaf.module.ai.aigc.project.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AigcProjectCandidateRegisteredEvent(
        UUID eventId,
        Long executionRunId,
        Long projectId,
        List<Long> objectVersionIds,
        Instant occurredAt) {

    public AigcProjectCandidateRegisteredEvent {
        objectVersionIds = objectVersionIds == null ? List.of() : List.copyOf(objectVersionIds);
    }
}
