package com.xuejiai.aaf.module.ai.aigc.project.api;

public record AigcDeliverableSlotEvidence(
        String stableKey,
        Long objectId,
        String contractRole,
        Long adoptedObjectVersionId,
        String validationStatus,
        Long activeExecutionReservationId,
        Long activeExecutionRunId) {}
