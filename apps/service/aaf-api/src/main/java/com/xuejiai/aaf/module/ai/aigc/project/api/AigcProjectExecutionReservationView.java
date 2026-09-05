package com.xuejiai.aaf.module.ai.aigc.project.api;

import java.util.List;

public record AigcProjectExecutionReservationView(
        Long id,
        Long executionSubmissionId,
        Long projectId,
        Long commandObjectId,
        Long targetGraphRevision,
        List<Long> frozenProjectObjectIds,
        String status,
        Long rootExecutionRunId) {

    public AigcProjectExecutionReservationView {
        frozenProjectObjectIds =
                frozenProjectObjectIds == null ? List.of() : List.copyOf(frozenProjectObjectIds);
    }
}
