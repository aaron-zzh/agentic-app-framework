package com.xuejiai.aaf.framework.intelligent.cognition.learning;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.RunId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.SessionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;

/** 仅供人工治理的学习候选；候选发布不等于自动修改任何运行时定义。 */
public record LearningCandidate(
        String candidateId,
        int schemaVersion,
        TenantId tenantId,
        TaskId taskId,
        ExecutionId executionId,
        RunId runId,
        SessionId sessionId,
        String sourceEventId,
        long sourceEventOffset,
        SourceKind sourceKind,
        String summaryCode,
        String safeSummary,
        List<EvidenceRef> evidenceRefs,
        Status status,
        String reviewerId,
        String decisionCode,
        Instant createdAt,
        Instant updatedAt,
        long revision) {

    public LearningCandidate {
        candidateId = requireReference(candidateId, "candidateId");
        Objects.requireNonNull(tenantId, "tenantId 不能为空");
        Objects.requireNonNull(taskId, "taskId 不能为空");
        Objects.requireNonNull(executionId, "executionId 不能为空");
        Objects.requireNonNull(runId, "runId 不能为空");
        Objects.requireNonNull(sessionId, "sessionId 不能为空");
        sourceEventId = requireReference(sourceEventId, "sourceEventId");
        Objects.requireNonNull(sourceKind, "sourceKind 不能为空");
        summaryCode = requireCode(summaryCode, "summaryCode");
        safeSummary = requireSafeSummary(safeSummary);
        evidenceRefs = evidenceRefs == null ? List.of() : List.copyOf(evidenceRefs);
        Objects.requireNonNull(status, "status 不能为空");
        Objects.requireNonNull(createdAt, "createdAt 不能为空");
        Objects.requireNonNull(updatedAt, "updatedAt 不能为空");
        if (schemaVersion < 1 || sourceEventOffset < 1 || revision < 0) {
            throw new IllegalArgumentException("LearningCandidate 版本或 offset 不合法");
        }
        if (status == Status.DRAFT && (reviewerId != null || decisionCode != null)) {
            throw new IllegalArgumentException("DRAFT 不允许携带审核决定");
        }
        if (status != Status.DRAFT) {
            reviewerId = requireReference(reviewerId, "reviewerId");
            decisionCode = requireCode(decisionCode, "decisionCode");
        }
    }

    public LearningCandidate transition(
            Status target, String reviewer, String decision, Instant at) {
        Objects.requireNonNull(target, "target 不能为空");
        Objects.requireNonNull(at, "at 不能为空");
        var allowed =
                switch (status) {
                    case DRAFT -> target == Status.REVIEWED || target == Status.REJECTED;
                    case REVIEWED -> target == Status.PUBLISHED || target == Status.REJECTED;
                    case PUBLISHED, REJECTED -> false;
                };
        if (!allowed) {
            throw new IllegalStateException(
                    "LearningCandidate 状态迁移不允许: " + status + " -> " + target);
        }
        return new LearningCandidate(
                candidateId,
                schemaVersion,
                tenantId,
                taskId,
                executionId,
                runId,
                sessionId,
                sourceEventId,
                sourceEventOffset,
                sourceKind,
                summaryCode,
                safeSummary,
                evidenceRefs,
                target,
                reviewer,
                decision,
                createdAt,
                at,
                revision + 1);
    }

    public enum SourceKind {
        TASK_COMPLETED,
        TASK_FAILED,
        HITL,
        VALIDATION
    }

    public enum Status {
        DRAFT,
        REVIEWED,
        PUBLISHED,
        REJECTED
    }

    public enum EvidenceKind {
        EVENT,
        ARTIFACT,
        APPROVAL,
        VALIDATION
    }

    public record EvidenceRef(EvidenceKind kind, String reference) {
        public EvidenceRef {
            Objects.requireNonNull(kind, "evidence kind 不能为空");
            reference = requireReference(reference, "evidence reference");
        }
    }

    private static String requireReference(String value, String field) {
        if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9:._/-]{0,255}")) {
            throw new IllegalArgumentException(field + " 必须是安全引用");
        }
        return value;
    }

    private static String requireCode(String value, String field) {
        if (value == null || !value.matches("[A-Z0-9][A-Z0-9_.-]{0,127}")) {
            throw new IllegalArgumentException(field + " 必须是安全代码");
        }
        return value;
    }

    private static String requireSafeSummary(String value) {
        if (value == null || !value.matches("[A-Z0-9][A-Z0-9_.-]{0,255}")) {
            throw new IllegalArgumentException("safeSummary 必须是安全摘要代码");
        }
        return value;
    }
}
