package com.xuejiai.aaf.framework.intelligent.action;

/** AI 动作目录项，由 SQL 策略层或其他配置源提供。 */
public record AiActionCatalogEntry(
        String action,
        String entitySlug,
        String displayName,
        String description,
        boolean enabled,
        String riskLevel,
        boolean requireConfirm,
        String permissionCodeOverride,
        String entitlementCode,
        String costExpression,
        String inputSchema,
        String outputSchema,
        Integer sortOrder) {}
