package com.xuejiai.aaf.module.ai.aigc.project.api;

public record AigcProjectExecutionReservationBindCommand(
        Long reservationId, Long executionSubmissionId, Long rootExecutionRunId) {}
