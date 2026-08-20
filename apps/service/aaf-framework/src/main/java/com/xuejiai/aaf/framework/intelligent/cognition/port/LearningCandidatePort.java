package com.xuejiai.aaf.framework.intelligent.cognition.port;

import java.time.Instant;
import java.util.Optional;

import com.xuejiai.aaf.framework.intelligent.cognition.learning.LearningCandidate;
import com.xuejiai.aaf.framework.intelligent.cognition.learning.LearningCandidate.Status;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;

/** LearningCandidate 唯一持久化端口，不包含任何定义自动应用能力。 */
public interface LearningCandidatePort {

    LearningCandidate create(LearningCandidate candidate);

    Optional<LearningCandidate> find(TenantId tenantId, String candidateId);

    LearningCandidate transition(
            TenantId tenantId,
            String candidateId,
            long expectedRevision,
            Status target,
            String reviewerId,
            String decisionCode,
            Instant at);
}
