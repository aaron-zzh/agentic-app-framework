package com.xuejiai.aaf.framework.intelligent.infrastructure.cognition.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LearningCandidateRepository
        extends JpaRepository<LearningCandidateEntity, String> {

    Optional<LearningCandidateEntity> findByTenantIdAndCandidateId(
            String tenantId, String candidateId);

    Optional<LearningCandidateEntity> findByTenantIdAndSourceEventId(
            String tenantId, String sourceEventId);
}
