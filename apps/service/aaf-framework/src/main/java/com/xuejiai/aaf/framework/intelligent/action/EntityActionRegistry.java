package com.xuejiai.aaf.framework.intelligent.action;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;

/** AI 可操作实体注册表。只有显式注册的实体会暴露给 AI。 */
@Component
public class EntityActionRegistry {

    private final Map<String, EntityActionAdapter> adapters = new LinkedHashMap<>();
    private final AiActionCatalogProvider catalogProvider;

    public EntityActionRegistry(
            List<EntityActionAdapter> adapters, ObjectProvider<AiActionCatalogProvider> catalogProvider) {
        for (var adapter : adapters) {
            this.adapters.put(adapter.entitySlug(), adapter);
        }
        this.catalogProvider = catalogProvider.getIfAvailable();
    }

    public EntityActionAdapter getRequired(String entitySlug) {
        if (entitySlug == null || entitySlug.isBlank()) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "实体标识不能为空");
        }
        var adapter = adapters.get(entitySlug.trim());
        if (adapter == null) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "AI 未开放该实体: " + entitySlug);
        }
        return adapter;
    }

    public List<Map<String, Object>> list() {
        return adapters.values().stream()
                .map(adapter -> Map.<String, Object>of(
                        "entitySlug", adapter.entitySlug(),
                        "entityName", adapter.entityName(),
                        "actions", visibleActions(adapter)))
                .filter(item -> !((List<?>) item.get("actions")).isEmpty())
                .toList();
    }

    public boolean isActionEnabled(EntityActionAdapter adapter, AiBusinessActionType action) {
        if (!adapter.supportedActions().contains(action.action())) {
            return false;
        }
        if (catalogProvider == null) {
            return true;
        }
        return catalogProvider
                .find(adapter.entitySlug(), action.action())
                .map(AiActionCatalogEntry::enabled)
                .orElse(false);
    }

    public String permissionCode(EntityActionAdapter adapter, AiBusinessActionType action) {
        if (catalogProvider != null) {
            var override =
                    catalogProvider
                            .find(adapter.entitySlug(), action.action())
                            .map(AiActionCatalogEntry::permissionCodeOverride)
                            .filter(code -> code != null && !code.isBlank())
                            .orElse(null);
            if (override != null) {
                return override;
            }
        }
        return adapter.permissionCode(action);
    }

    public AiActionCatalogEntry catalogEntry(EntityActionAdapter adapter, AiBusinessActionType action) {
        return catalogProvider == null
                ? null
                : catalogProvider.find(adapter.entitySlug(), action.action()).orElse(null);
    }

    private List<Map<String, Object>> visibleActions(EntityActionAdapter adapter) {
        return adapter.supportedActions().stream()
                .map(AiBusinessActionType::from)
                .filter(action -> isActionEnabled(adapter, action))
                .map(action -> actionItem(adapter, action))
                .toList();
    }

    private Map<String, Object> actionItem(EntityActionAdapter adapter, AiBusinessActionType action) {
        var entry = catalogProvider == null
                ? null
                : catalogProvider.find(adapter.entitySlug(), action.action()).orElse(null);
        return Map.of(
                "action", action.action(),
                "displayName", entry == null ? action.action() : safe(entry.displayName()),
                "description", entry == null ? "" : safe(entry.description()),
                "riskLevel", entry == null ? "LOW" : safe(entry.riskLevel()),
                "requireConfirm", entry != null && entry.requireConfirm(),
                "inputSchema", entry == null ? "" : safe(entry.inputSchema()));
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
