package com.xuejiai.aaf.framework.engine.tool;

/** 工具目录项，由 SQL 策略层或其他配置源提供。 */
public record ToolCatalogEntry(
        String toolName,
        String source,
        boolean enabled,
        ToolType type,
        String category,
        ToolRiskLevel riskLevel,
        boolean readOnly,
        boolean reversible,
        boolean requireConfirm,
        boolean idempotencyRequired,
        String permissionCode,
        String entitlementCode,
        String costExpression,
        String inputSchema,
        String outputSchema,
        Integer sortOrder,
        /* 工具中文名，仅用于界面展示；未配置时为 null。 */
        String displayName) {}
