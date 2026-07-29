package com.xuejiai.aaf.framework.intelligent.cognition.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;

/** Cognition 持有的长期记忆事实及其作用域。 */
public record MemoryRecord(
        String memoryId,
        MemorySubject subject,
        String content,
        String redactedSummary,
        double importance,
        double confidence,
        PrivacyLevel privacy,
        List<String> tags,
        Instant expiresAt,
        Instant createdAt) {

    public MemoryRecord {
        memoryId = requireText(memoryId, "memoryId");
        Objects.requireNonNull(subject, "subject 不能为空");
        content = requireText(content, "content");
        redactedSummary = requireText(redactedSummary, "redactedSummary");
        if (redactedSummary.length() > 256) {
            throw new IllegalArgumentException("记忆脱敏摘要不能超过 256 字符");
        }
        requireScore(importance, "importance");
        requireScore(confidence, "confidence");
        Objects.requireNonNull(privacy, "privacy 不能为空");
        tags = tags == null ? List.of() : List.copyOf(tags);
        Objects.requireNonNull(createdAt, "createdAt 不能为空");
        if (subject.kind() == SubjectKind.VISITOR && expiresAt == null) {
            throw new IllegalArgumentException("访客记忆必须设置 TTL");
        }
    }

    public SourceReference reference() {
        return new SourceReference(memoryId, subject.scopeKey(), redactedSummary);
    }

    public record MemorySubject(TenantId tenantId, SubjectKind kind, String subjectId) {
        public MemorySubject {
            Objects.requireNonNull(tenantId, "tenantId 不能为空");
            Objects.requireNonNull(kind, "subject kind 不能为空");
            subjectId = requireText(subjectId, "subjectId");
        }

        public String scopeKey() {
            return "%s:%s:%s".formatted(tenantId.value(), kind.name(), subjectId);
        }
    }

    public record SourceReference(String memoryId, String scope, String redactedSummary) {
        public SourceReference {
            memoryId = requireText(memoryId, "memoryId");
            scope = requireText(scope, "scope");
            redactedSummary = requireText(redactedSummary, "redactedSummary");
        }
    }

    public record Candidate(String content, List<String> tags, Instant eventAt) {
        public Candidate {
            content = requireText(content, "content");
            tags = tags == null ? List.of() : List.copyOf(tags);
            Objects.requireNonNull(eventAt, "eventAt 不能为空");
        }
    }

    public record Assessment(
            Candidate candidate,
            double importance,
            double confidence,
            PrivacyLevel privacy,
            String redactedSummary,
            boolean accepted,
            String reason) {
        public Assessment {
            Objects.requireNonNull(candidate, "candidate 不能为空");
            requireScore(importance, "importance");
            requireScore(confidence, "confidence");
            Objects.requireNonNull(privacy, "privacy 不能为空");
            redactedSummary = requireText(redactedSummary, "redactedSummary");
            reason = requireText(reason, "reason");
        }
    }

    public record ExplicitConfirmation(
            boolean confirmed, String confirmedBy, String reason, Instant confirmedAt) {
        public ExplicitConfirmation {
            confirmedBy = requireText(confirmedBy, "confirmedBy");
            reason = requireText(reason, "reason");
            Objects.requireNonNull(confirmedAt, "confirmedAt 不能为空");
        }

        public void requireConfirmed(String operation) {
            if (!confirmed) {
                throw new IllegalStateException(operation + " 必须获得用户显式确认");
            }
        }
    }

    public enum SubjectKind {
        USER,
        VISITOR
    }

    public enum PrivacyLevel {
        PUBLIC,
        PERSONAL,
        SENSITIVE,
        SECRET
    }

    public enum ConflictDecision {
        ADD,
        DUPLICATE,
        REPLACE,
        CONFLICT_REQUIRES_CONFIRMATION
    }

    public record ConflictResolution(
            ConflictDecision decision, String existingMemoryId, String reason) {
        public ConflictResolution {
            Objects.requireNonNull(decision, "decision 不能为空");
            reason = requireText(reason, "reason");
        }
    }

    public record MutationMetadata(Map<String, String> values) {
        public MutationMetadata {
            values = values == null ? Map.of() : Map.copyOf(values);
        }
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name + " 不能为空");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " 不能为空白");
        }
        return value.trim();
    }

    private static void requireScore(double value, String name) {
        if (!Double.isFinite(value) || value < 0 || value > 1) {
            throw new IllegalArgumentException(name + " 必须在 0 到 1 之间");
        }
    }
}
