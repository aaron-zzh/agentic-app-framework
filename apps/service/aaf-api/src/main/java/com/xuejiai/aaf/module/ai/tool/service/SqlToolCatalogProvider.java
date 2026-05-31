package com.xuejiai.aaf.module.ai.tool.service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.framework.engine.tool.ToolCatalogEntry;
import com.xuejiai.aaf.framework.engine.tool.ToolCatalogProvider;
import com.xuejiai.aaf.framework.engine.tool.ToolRiskLevel;
import com.xuejiai.aaf.framework.engine.tool.ToolType;
import com.xuejiai.aaf.module.ai.tool.domain.AiToolCatalog;
import com.xuejiai.aaf.module.ai.tool.repository.AiToolCatalogRepository;

import lombok.RequiredArgsConstructor;

/** 基于数据库的 AI 工具目录策略提供者。 */
@Service
@RequiredArgsConstructor
public class SqlToolCatalogProvider implements ToolCatalogProvider {

    private final AiToolCatalogRepository repository;

    @Override
    @Transactional(readOnly = true)
    public Optional<ToolCatalogEntry> find(String toolName) {
        return repository.findByToolNameAndDeletedFalse(toolName).map(this::toEntry);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ToolCatalogEntry> listEnabled() {
        return repository.findByEnabledTrueAndDeletedFalseOrderBySortOrderAscIdAsc().stream()
                .map(this::toEntry)
                .toList();
    }

    private ToolCatalogEntry toEntry(AiToolCatalog entity) {
        return new ToolCatalogEntry(
                entity.getToolName(),
                entity.getSource(),
                Boolean.TRUE.equals(entity.getEnabled()),
                enumValue(ToolType.class, entity.getToolType(), ToolType.FUNCTION),
                entity.getCategory(),
                enumValue(ToolRiskLevel.class, entity.getRiskLevel(), ToolRiskLevel.LOW),
                Boolean.TRUE.equals(entity.getReadOnly()),
                Boolean.TRUE.equals(entity.getRequireConfirm()),
                entity.getPermissionCode(),
                entity.getEntitlementCode(),
                entity.getCostExpression(),
                entity.getInputSchema(),
                entity.getOutputSchema(),
                entity.getSortOrder());
    }

    private <E extends Enum<E>> E enumValue(Class<E> type, String value, E defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return defaultValue;
        }
    }
}
