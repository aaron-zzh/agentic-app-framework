package com.xuejiai.aaf.framework.security.authorization;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** 一次性持久化 challenge 存储 SPI。 */
public interface AuthorizationChallengeStore {

    UUID create(PendingChallenge challenge);

    boolean approve(UUID challengeId, Long subjectId, Instant now);

    Optional<Challenge> findApproved(UUID challengeId, Long subjectId, Instant now);

    boolean consume(UUID challengeId, Long subjectId, Instant now);

    record PendingChallenge(
            AuthorizationSubject subject,
            AuthorizationTarget target,
            String requestDigest,
            Long policyId,
            long policyVersion,
            String snapshotVersion,
            Instant expiresAt) {}

    record Challenge(
            UUID id,
            AuthorizationSubject subject,
            AuthorizationTarget target,
            String requestDigest,
            Long policyId,
            long policyVersion,
            String snapshotVersion,
            Instant expiresAt) {}
}
