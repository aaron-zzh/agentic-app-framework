package com.xuejiai.aaf.framework.intelligent.cognition.application;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;

import com.xuejiai.aaf.framework.intelligent.cognition.model.MemoryRecord;
import com.xuejiai.aaf.framework.intelligent.cognition.model.MemoryRecord.ConflictDecision;
import com.xuejiai.aaf.framework.intelligent.cognition.model.MemoryRecord.MemorySubject;
import com.xuejiai.aaf.framework.intelligent.cognition.port.MemoryGovernancePort;
import com.xuejiai.aaf.framework.intelligent.cognition.port.MemoryWritePort;

/** Cognition 唯一长期记忆写入流水线。 */
public final class MemoryGovernanceService {

    private static final double MIN_IMPORTANCE = 0.55;
    private static final double MIN_CONFIDENCE = 0.70;
    private static final Duration VISITOR_TTL = Duration.ofHours(24);

    private final MemoryGovernancePort governance;
    private final MemoryWritePort writer;

    public MemoryGovernanceService(MemoryGovernancePort governance, MemoryWritePort writer) {
        this.governance = Objects.requireNonNull(governance, "governance 不能为空");
        this.writer = Objects.requireNonNull(writer, "writer 不能为空");
    }

    public WriteOutcome learn(
            MemorySubject subject, String userMessage, String assistantReply, Instant at) {
        var accepted = new ArrayList<MemoryRecord>();
        var rejected = 0;
        for (var candidate : governance.extract(userMessage, assistantReply, at)) {
            var assessment = governance.assess(candidate);
            if (!assessment.accepted()
                    || assessment.importance() < MIN_IMPORTANCE
                    || assessment.confidence() < MIN_CONFIDENCE
                    || assessment.privacy() == MemoryRecord.PrivacyLevel.SECRET) {
                rejected++;
                continue;
            }
            var resolution = governance.resolve(subject, assessment, at);
            if (resolution.decision() == ConflictDecision.DUPLICATE
                    || resolution.decision() == ConflictDecision.CONFLICT_REQUIRES_CONFIRMATION) {
                rejected++;
                continue;
            }
            var expiresAt =
                    subject.kind() == MemoryRecord.SubjectKind.VISITOR
                            ? MemoryWritePort.anonymousExpiry(at, VISITOR_TTL)
                            : null;
            accepted.add(
                    new MemoryRecord(
                            UUID.randomUUID().toString(),
                            subject,
                            assessment.candidate().content(),
                            assessment.redactedSummary(),
                            assessment.importance(),
                            assessment.confidence(),
                            assessment.privacy(),
                            assessment.candidate().tags(),
                            expiresAt,
                            at));
        }
        var stored = accepted.isEmpty() ? java.util.List.<MemoryRecord>of() : writer.append(accepted);
        return new WriteOutcome(stored.size(), rejected);
    }

    public record WriteOutcome(int stored, int rejected) {}
}
