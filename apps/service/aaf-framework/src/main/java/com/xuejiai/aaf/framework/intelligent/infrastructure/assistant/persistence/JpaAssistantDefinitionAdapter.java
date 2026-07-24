package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence;

import java.util.Objects;
import java.util.Optional;

import com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantDefinition;
import com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantVersion;
import com.xuejiai.aaf.framework.intelligent.assistant.port.AssistantDefinitionPort;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.AssistantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;

/** PostgreSQL/JPA Assistant 定义读取适配器。 */
public final class JpaAssistantDefinitionAdapter implements AssistantDefinitionPort {

    private final AssistantDefinitionVersionRepository repository;

    public JpaAssistantDefinitionAdapter(AssistantDefinitionVersionRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository 不能为空");
    }

    @Override
    public Optional<AssistantDefinition> findByIdAndVersion(
            TenantId tenantId, AssistantId assistantId, AssistantVersion version) {
        Objects.requireNonNull(tenantId, "tenantId 不能为空");
        var tenantDefinition =
                repository.findByTenantIdAndAssistantIdAndDefinitionVersion(
                        tenantId.value(), assistantId.value(), version.value());
        if (tenantDefinition.isPresent()) {
            return tenantDefinition.map(AssistantDefinitionVersionEntity::getDefinition);
        }
        return repository
                .findByTenantIdAndAssistantIdAndDefinitionVersion(
                        AssistantDefinitionVersionEntity.SYSTEM_TENANT_ID,
                        assistantId.value(),
                        version.value())
                .map(AssistantDefinitionVersionEntity::getDefinition);
    }
}
