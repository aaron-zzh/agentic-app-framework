package com.xuejiai.aaf.module.system.authorization;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.framework.security.authorization.AuthorizationAuditSink;
import com.xuejiai.aaf.framework.security.authorization.AuthorizationEffect;
import com.xuejiai.aaf.framework.security.authorization.AuthorizationSubject;
import com.xuejiai.aaf.framework.security.authorization.AuthorizationTarget;

import lombok.RequiredArgsConstructor;

/** 授权审计持久化实现。 */
@Service
@RequiredArgsConstructor
public class AuthorizationAuditService implements AuthorizationAuditSink {

    private final AuthorizationAuditRepository repository;
    private final OperatorContext operatorContext;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Event event) {
        var subject = event.subject();
        var target = event.target();
        var entity = new AuthorizationAudit();
        entity.setEventType(event.eventType());
        entity.setOperatorId(subject == null ? null : subject.operatorId());
        entity.setSubjectId(subject == null ? null : subject.subjectId());
        entity.setTenantId(subject == null ? null : subject.tenantId());
        entity.setOrgId(subject == null ? null : subject.tenantId());
        entity.setWorkspaceId(subject == null ? null : subject.workspaceId());
        entity.setOwnerId(subject == null ? null : subject.subjectId());
        entity.setResource(target == null ? null : target.resource());
        entity.setAction(target == null ? null : target.action());
        entity.setObjectId(target == null ? null : target.objectId());
        entity.setLayer(event.layer() == null ? null : event.layer().name());
        entity.setEffect(event.effect().name());
        entity.setPolicyId(event.policyId());
        entity.setPolicyVersion(event.policyVersion());
        entity.setSnapshotVersion(event.snapshotVersion());
        entity.setShadow(event.shadow());
        entity.setChallengeId(event.challengeId());
        entity.setReason(event.reason());
        entity.setOccurredAt(event.occurredAt());
        repository.save(entity);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordPolicyLifecycle(
            String eventType, AccessPolicy policy, String snapshotVersion, String reason) {
        var subject =
                new AuthorizationSubject(
                        operatorContext.currentOperatorId().orElse(null),
                        operatorContext.currentOwnerId().orElse(null),
                        policy.getOrgId(),
                        policy.getWorkspaceId());
        record(
                new Event(
                        eventType,
                        subject,
                        new AuthorizationTarget(
                                policy.getTargetResource(), policy.getTargetAction(), null),
                        null,
                        AuthorizationEffect.ALLOW,
                        policy.getId(),
                        policy.getPublishedVersion(),
                        snapshotVersion,
                        false,
                        null,
                        reason,
                        Instant.now()));
    }
}
