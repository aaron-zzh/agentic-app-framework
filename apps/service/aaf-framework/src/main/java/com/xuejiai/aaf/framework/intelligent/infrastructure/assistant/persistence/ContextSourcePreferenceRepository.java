package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ContextSourcePreferenceRepository
        extends JpaRepository<ContextSourcePreferenceEntity, Long> {

    List<ContextSourcePreferenceEntity> findByTenantIdAndUserIdAndAssistantId(
            String tenantId, String userId, String assistantId);

    Optional<ContextSourcePreferenceEntity>
            findByTenantIdAndUserIdAndAssistantIdAndSourceTypeAndSourceKey(
                    String tenantId,
                    String userId,
                    String assistantId,
                    String sourceType,
                    String sourceKey);
}
