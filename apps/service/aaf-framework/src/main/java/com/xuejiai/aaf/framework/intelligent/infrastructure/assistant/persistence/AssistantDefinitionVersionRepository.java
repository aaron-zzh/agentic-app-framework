package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** Assistant 定义版本仓储。 */
public interface AssistantDefinitionVersionRepository
        extends JpaRepository<AssistantDefinitionVersionEntity, Long> {

    Optional<AssistantDefinitionVersionEntity>
            findByTenantIdAndAssistantIdAndDefinitionVersion(
                    String tenantId, String assistantId, Long definitionVersion);

    Optional<AssistantDefinitionVersionEntity>
            findFirstByTenantIdAndSystemKeyOrderByDefinitionVersionDesc(
                    String tenantId, String systemKey);
}
