package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PromptEnvelopeBodyRepository
        extends JpaRepository<PromptEnvelopeBodyEntity, Long> {

    Optional<PromptEnvelopeBodyEntity> findByTenantIdAndExecutionIdAndEnvelopeSeq(
            String tenantId, String executionId, Integer envelopeSeq);
}
