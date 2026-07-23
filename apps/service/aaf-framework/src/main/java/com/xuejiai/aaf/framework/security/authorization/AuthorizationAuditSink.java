package com.xuejiai.aaf.framework.security.authorization;

import java.time.Instant;
import java.util.UUID;

/** 授权审计持久化 SPI。 */
public interface AuthorizationAuditSink {

    void record(Event event);

    record Event(
            String eventType,
            AuthorizationSubject subject,
            AuthorizationTarget target,
            AuthorizationLayer layer,
            AuthorizationEffect effect,
            Long policyId,
            Long policyVersion,
            String snapshotVersion,
            boolean shadow,
            UUID challengeId,
            String reason,
            Instant occurredAt) {}
}
