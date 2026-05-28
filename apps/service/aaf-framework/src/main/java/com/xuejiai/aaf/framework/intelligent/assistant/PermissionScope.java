package com.xuejiai.aaf.framework.intelligent.assistant;

import java.util.List;

import com.xuejiai.aaf.common.enums.OverLimitAction;
import com.xuejiai.aaf.common.enums.RiskLevel;

/**
 * 助理权限边界配置——用户为 AI 助理配置的 scope 白名单。
 *
 * <p>存储为 AssistantDefinition 的 JSONB 字段。AI 实际权限 = 委托者权限 ∩ 本配置。
 */
public record PermissionScope(
        /** 允许调用的工具名列表（null 表示不限制） */
        List<String> allowedTools,
        /** 允许访问的资源模式（如 "space:my-workspace/*", "document:*"） */
        List<String> allowedResources,
        /** 允许的操作类型（如 "read", "write", "execute", "delete"） */
        List<String> allowedOperations,
        /** 可自动执行的最高风险等级 */
        RiskLevel maxAutoRiskLevel,
        /** 超出权限时的处理策略 */
        OverLimitAction overLimitAction) {

    /** 默认配置：允许读写 + 中风险自动 + 超出时询问 */
    public static PermissionScope defaults() {
        return new PermissionScope(
                null,
                List.of("*"),
                List.of("read", "write", "execute"),
                RiskLevel.MEDIUM,
                OverLimitAction.ASK);
    }

    /** 判断工具是否在白名单内 */
    public boolean isToolAllowed(String toolName) {
        return allowedTools == null || allowedTools.contains(toolName);
    }

    /** 判断操作是否在白名单内 */
    public boolean isOperationAllowed(String operation) {
        return allowedOperations == null || allowedOperations.contains(operation);
    }

    /** 判断资源是否在白名单内（支持通配符） */
    public boolean isResourceAllowed(String resource) {
        if (allowedResources == null) return true;
        return allowedResources.stream().anyMatch(pattern -> matchResource(pattern, resource));
    }

    private static boolean matchResource(String pattern, String resource) {
        if ("*".equals(pattern)) return true;
        if (pattern.endsWith("/*")) {
            var prefix = pattern.substring(0, pattern.length() - 1);
            return resource.startsWith(prefix);
        }
        return pattern.equals(resource);
    }
}
