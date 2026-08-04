package com.xuejiai.aaf.module.ai.aigc.project.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AigcProjectReviewApprovedEvent(
        UUID eventId,
        Long projectId,
        Long reviewObjectId,
        List<Long> approvedDeliverableObjectIds,
        Instant occurredAt) {

    public AigcProjectReviewApprovedEvent {
        approvedDeliverableObjectIds =
                approvedDeliverableObjectIds == null
                        ? List.of()
                        : List.copyOf(approvedDeliverableObjectIds);
    }
}
