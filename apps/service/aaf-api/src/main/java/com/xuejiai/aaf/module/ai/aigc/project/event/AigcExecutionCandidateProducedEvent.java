package com.xuejiai.aaf.module.ai.aigc.project.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AigcExecutionCandidateProducedEvent(
        UUID eventId,
        Long executionRunId,
        Long executionSubmissionId,
        Long executionReservationId,
        Long rootExecutionRunId,
        Long projectId,
        Long projectObjectId,
        List<AigcObjectCandidatePayload> objectCandidates,
        List<Long> mediaVersionIds,
        Instant occurredAt) {

    public AigcExecutionCandidateProducedEvent {
        objectCandidates = objectCandidates == null ? List.of() : List.copyOf(objectCandidates);
        mediaVersionIds = mediaVersionIds == null ? List.of() : List.copyOf(mediaVersionIds);
    }
}
