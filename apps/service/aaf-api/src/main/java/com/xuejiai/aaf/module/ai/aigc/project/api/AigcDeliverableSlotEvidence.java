package com.xuejiai.aaf.module.ai.aigc.project.api;

import java.util.List;

public record AigcDeliverableSlotEvidence(
        String stableKey,
        Long objectId,
        String contractRole,
        Long adoptedObjectVersionId,
        Long documentVersionId,
        List<Long> mediaVersionIds,
        String validationStatus,
        Long activeExecutionReservationId,
        Long activeExecutionRunId) {

    public AigcDeliverableSlotEvidence {
        mediaVersionIds = mediaVersionIds == null ? List.of() : List.copyOf(mediaVersionIds);
    }
}
