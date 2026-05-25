package com.xuejiai.aaf.framework.engine.tool;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 工具调用分发器——权限检查 → 路由 → 执行 → 结果封装。
 *
 * <p>统一调用入口，不管调用方是 Agent/REST/A2A 都走这里。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ToolCallDispatcher {

    private final ToolRegistry registry;
    private final ToolPermissionChecker permissionChecker;

    /**
     * 执行工具调用（无权限检查，Agent 内部调用）。
     */
    public ToolCallResult dispatch(String functionName, String arguments) {
        return doDispatch(functionName, arguments);
    }

    /**
     * 执行工具调用（含权限检查，外部调用）。
     */
    public ToolCallResult dispatchWithPermission(String sessionId, Long userId, String roleId, String functionName, String arguments) {
        var meta = registry.listAll().stream()
                .filter(m -> m.name().equals(functionName))
                .findFirst()
                .orElse(null);
        if (meta == null) {
            return ToolCallResult.error(functionName, "工具未注册: " + functionName);
        }

        var hasRolePermission = registry.resolveForRole(roleId).stream()
                .anyMatch(cb -> cb.getToolDefinition().name().equals(functionName));
        var permission = permissionChecker.check(sessionId, userId, functionName, meta.riskLevel(), hasRolePermission);

        return switch (permission) {
            case GRANTED, AUTO_GRANTED -> doDispatch(functionName, arguments);
            case PENDING_APPROVAL -> ToolCallResult.pendingApproval(functionName,
                    "工具 [%s] 需要授权确认（风险等级: %s）".formatted(functionName, meta.riskLevel()));
            case DENIED -> ToolCallResult.error(functionName, "工具 [%s] 权限不足，需管理员审批".formatted(functionName));
        };
    }

    private ToolCallResult doDispatch(String functionName, String arguments) {
        var callback = registry.getCallback(functionName).orElse(null);
        if (callback == null) {
            return ToolCallResult.error(functionName, "工具未注册: " + functionName);
        }
        try {
            var result = callback.call(arguments);
            log.debug("工具调用成功: {} -> {}", functionName, truncate(result));
            return ToolCallResult.success(functionName, result);
        } catch (Exception e) {
            log.warn("工具调用失败: {} - {}", functionName, e.getMessage());
            return ToolCallResult.error(functionName, e.getMessage());
        }
    }

    private String truncate(String s) {
        return s != null && s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }

    /** 工具调用结果 */
    public record ToolCallResult(
            String functionName, boolean success, String output, String error, boolean pendingApproval) {
        public static ToolCallResult success(String name, String output) {
            return new ToolCallResult(name, true, output, null, false);
        }
        public static ToolCallResult error(String name, String error) {
            return new ToolCallResult(name, false, null, error, false);
        }
        public static ToolCallResult pendingApproval(String name, String message) {
            return new ToolCallResult(name, false, message, null, true);
        }
    }
}
