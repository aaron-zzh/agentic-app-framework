package com.xuejiai.aaf.module.ai.aigc.project.api;

import java.util.List;

public record AigcProjectExecutionReservationCommand(
        Long executionSubmissionId,
        Long projectId,
        Long commandObjectId,
        String actionKey,
        List<Long> requestedProjectObjectIds,
        Long expectedGraphRevision,
        String idempotencyKey) {

    public AigcProjectExecutionReservationCommand {
        requestedProjectObjectIds =
                requestedProjectObjectIds == null
                        ? List.of()
                        : List.copyOf(requestedProjectObjectIds);
    }
}
