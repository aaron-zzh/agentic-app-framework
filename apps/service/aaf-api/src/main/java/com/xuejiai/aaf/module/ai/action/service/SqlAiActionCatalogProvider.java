package com.xuejiai.aaf.module.ai.action.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.framework.intelligent.action.AiActionCatalogEntry;
import com.xuejiai.aaf.framework.intelligent.action.AiActionCatalogProvider;
import com.xuejiai.aaf.module.ai.action.domain.AiActionCatalog;
import com.xuejiai.aaf.module.ai.action.repository.AiActionCatalogRepository;

import lombok.RequiredArgsConstructor;

/** 基于数据库的 AI 动作目录策略提供者。 */
@Service
@RequiredArgsConstructor
public class SqlAiActionCatalogProvider implements AiActionCatalogProvider {

    private final AiActionCatalogRepository repository;

    @Override
    @Transactional(readOnly = true)
    public Optional<AiActionCatalogEntry> find(String entitySlug, String action) {
        return repository
                .findByEntitySlugAndActionKeyAndDeletedFalse(entitySlug, action)
                .map(this::toEntry);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AiActionCatalogEntry> listEnabled() {
        return repository.findByEnabledTrueAndDeletedFalseOrderBySortOrderAscIdAsc().stream()
                .map(this::toEntry)
                .toList();
    }

    private AiActionCatalogEntry toEntry(AiActionCatalog entity) {
        return new AiActionCatalogEntry(
                entity.getActionKey(),
                entity.getEntitySlug(),
                entity.getDisplayName(),
                entity.getDescription(),
                Boolean.TRUE.equals(entity.getEnabled()),
                entity.getRiskLevel(),
                Boolean.TRUE.equals(entity.getRequireConfirm()),
                entity.getPermissionCodeOverride(),
                entity.getEntitlementCode(),
                entity.getCostExpression(),
                entity.getInputSchema(),
                entity.getOutputSchema(),
                entity.getSortOrder());
    }
}
