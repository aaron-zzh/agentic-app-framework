package com.xuejiai.aaf.module.ai.aigc.project.api;

public record AigcProjectExecutionReservationReleaseCommand(
        Long reservationId,
        Long executionSubmissionId,
        Long rootExecutionRunId,
        String releaseReason) {}
