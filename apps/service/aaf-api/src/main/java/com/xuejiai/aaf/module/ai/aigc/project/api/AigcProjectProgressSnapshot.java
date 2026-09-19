package com.xuejiai.aaf.module.ai.aigc.project.api;

import java.time.Instant;
import java.util.List;

public record AigcProjectProgressSnapshot(
        int projectionVersion,
        Instant updatedAt,
        Long projectId,
        Integer projectVersion,
        Long graphRevision,
        Void currentTask,
        List<ObjectProgressRef> objects,
        List<AigcObjectReadiness> readiness,
        List<AigcProjectProgressEvidencePort.ExecutionProgressRef> executions,
        List<CandidateDecisionRef> candidates,
        DeliveryProgress delivery,
        AigcProjectProgressEvidencePort.ActualCreditSummary credits) {

    public AigcProjectProgressSnapshot {
        objects = objects == null ? List.of() : List.copyOf(objects);
        readiness = readiness == null ? List.of() : List.copyOf(readiness);
        executions = executions == null ? List.of() : List.copyOf(executions);
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }

    public record ObjectProgressRef(
            Long objectId,
            String maturity,
            String contractRole,
            AigcObjectReadinessState readiness,
            AigcProjectProgressEvidencePort.ActualCreditSummary credits) {}

    public record CandidateDecisionRef(
            Long candidateVersionId,
            Long objectId,
            String status,
            Long executionRunId,
            Long documentVersionId,
            List<Long> mediaVersionIds) {
        public CandidateDecisionRef {
            mediaVersionIds = mediaVersionIds == null ? List.of() : List.copyOf(mediaVersionIds);
        }
    }

    public record DeliveryProgress(
            boolean completionSatisfied,
            int reviewCount,
            long pendingReviewCount,
            long activeWorkCount,
            long publicationCount,
            List<String> blockers) {
        public DeliveryProgress {
            blockers = blockers == null ? List.of() : List.copyOf(blockers);
        }
    }
}
