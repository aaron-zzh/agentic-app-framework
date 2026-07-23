package com.xuejiai.aaf.module.system.authorization;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.framework.security.authorization.AuthorizationChallengeStore;
import com.xuejiai.aaf.framework.security.authorization.AuthorizationSubject;
import com.xuejiai.aaf.framework.security.authorization.AuthorizationTarget;

import lombok.RequiredArgsConstructor;

/** JPA challenge 存储；创建独立提交，批准与消费使用条件更新，消费加入调用方业务事务。 */
@Service
@RequiredArgsConstructor
public class JpaAuthorizationChallengeStore implements AuthorizationChallengeStore {

    private final AuthorizationChallengeRepository repository;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID create(PendingChallenge pending) {
        var subject = pending.subject();
        var target = pending.target();
        if (subject == null
                || subject.subjectId() == null
                || target == null
                || !target.hasPolicyTarget()
                || pending.requestDigest() == null
                || pending.requestDigest().isBlank()
                || pending.policyId() == null
                || pending.policyVersion() <= 0
                || pending.snapshotVersion() == null
                || pending.snapshotVersion().isBlank()
                || pending.expiresAt() == null
                || !pending.expiresAt().isAfter(Instant.now())) {
            throw new IllegalArgumentException("challenge 绑定字段不完整或已过期");
        }
        var entity = new AuthorizationChallenge();
        entity.setId(UUID.randomUUID());
        entity.setOperatorId(subject.operatorId());
        entity.setSubjectId(subject.subjectId());
        entity.setTenantId(subject.tenantId());
        entity.setWorkspaceId(subject.workspaceId());
        entity.setResource(target.resource());
        entity.setAction(target.action());
        entity.setObjectId(target.objectId());
        entity.setRequestDigest(pending.requestDigest());
        entity.setPolicyId(pending.policyId());
        entity.setPolicyVersion(pending.policyVersion());
        entity.setSnapshotVersion(pending.snapshotVersion());
        entity.setStatus("PENDING");
        entity.setExpiresAt(pending.expiresAt());
        entity.setCreateTime(Instant.now());
        return repository.save(entity).getId();
    }

    @Override
    @Transactional
    public boolean approve(UUID challengeId, Long subjectId, Instant now) {
        return repository.approve(challengeId, subjectId, now) == 1;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Challenge> findApproved(UUID challengeId, Long subjectId, Instant now) {
        return repository.findApproved(challengeId, subjectId, now).map(this::toChallenge);
    }

    @Override
    @Transactional
    public boolean consume(UUID challengeId, Long subjectId, Instant now) {
        return repository.consume(challengeId, subjectId, now) == 1;
    }

    private Challenge toChallenge(AuthorizationChallenge entity) {
        return new Challenge(
                entity.getId(),
                new AuthorizationSubject(
                        entity.getOperatorId(),
                        entity.getSubjectId(),
                        entity.getTenantId(),
                        entity.getWorkspaceId()),
                new AuthorizationTarget(
                        entity.getResource(), entity.getAction(), entity.getObjectId()),
                entity.getRequestDigest(),
                entity.getPolicyId(),
                entity.getPolicyVersion(),
                entity.getSnapshotVersion(),
                entity.getExpiresAt());
    }
}
