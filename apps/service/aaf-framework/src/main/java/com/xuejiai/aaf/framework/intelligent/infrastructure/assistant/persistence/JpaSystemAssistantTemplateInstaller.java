package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence;

import java.util.Objects;

import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantDefinition;
import com.xuejiai.aaf.framework.intelligent.assistant.port.SystemAssistantTemplateInstaller;

/** 系统 Assistant 模板的版本化 JPA 安装器。 */
public final class JpaSystemAssistantTemplateInstaller implements SystemAssistantTemplateInstaller {

    private final AssistantDefinitionVersionRepository repository;

    public JpaSystemAssistantTemplateInstaller(AssistantDefinitionVersionRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository 不能为空");
    }

    @Override
    @Transactional
    public InstallationResult install(AssistantDefinition systemTemplate) {
        Objects.requireNonNull(systemTemplate, "systemTemplate 不能为空");
        var current =
                repository
                        .findFirstByTenantIdAndSystemKeyOrderByDefinitionVersionDesc(
                                AssistantDefinitionVersionEntity.SYSTEM_TENANT_ID,
                                systemTemplate.systemKey())
                        .orElse(null);
        if (current != null) {
            var currentVersion = current.getDefinitionVersion();
            if (currentVersion > systemTemplate.version().value()) {
                return result(systemTemplate, InstallationOutcome.UNCHANGED);
            }
            if (currentVersion == systemTemplate.version().value()) {
                if (!current.getDefinition().equals(systemTemplate)) {
                    throw new IllegalStateException(
                            "系统 Assistant 已发布版本内容不可变: "
                                    + systemTemplate.systemKey()
                                    + "@"
                                    + systemTemplate.version());
                }
                return result(systemTemplate, InstallationOutcome.UNCHANGED);
            }
        }

        var entity = new AssistantDefinitionVersionEntity();
        entity.setTenantId(AssistantDefinitionVersionEntity.SYSTEM_TENANT_ID);
        entity.setAssistantId(systemTemplate.assistantId().value());
        entity.setSystemKey(systemTemplate.systemKey());
        entity.setDefinitionVersion(systemTemplate.version().value());
        entity.setDefinition(systemTemplate);
        repository.saveAndFlush(entity);
        return result(
                systemTemplate,
                current == null ? InstallationOutcome.CREATED : InstallationOutcome.UPGRADED);
    }

    private static InstallationResult result(
            AssistantDefinition definition, InstallationOutcome outcome) {
        return new InstallationResult(definition.systemKey(), definition.version(), outcome);
    }
}
