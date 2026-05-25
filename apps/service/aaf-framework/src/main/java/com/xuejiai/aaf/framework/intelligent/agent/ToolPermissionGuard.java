package com.xuejiai.aaf.framework.intelligent.agent;

import java.util.List;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.engine.tool.ToolPermissionChecker;
import com.xuejiai.aaf.framework.engine.tool.ToolPermissionChecker.PermissionResult;
import com.xuejiai.aaf.framework.engine.tool.ToolRegistry;
import com.xuejiai.aaf.framework.engine.tool.ToolRiskLevel;
import com.xuejiai.aaf.framework.security.ActorContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 工具权限拦截——通过装饰 ToolCallback 实现 Agent 内部工具调用的权限检查。
 *
 * <p>组合模式：包装原始 ToolCallback，在 call() 前执行权限检查。
 * 这样无论 AgentScope 内部如何调用工具，都会经过权限拦截。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolPermissionGuard {

    private final ToolPermissionChecker permissionChecker;
    private final ToolRegistry toolRegistry;
    private final ActorContext actorContext;

    /**
     * 包装工具列表，为每个工具加上权限检查。
     */
    public List<ToolCallback> guard(List<ToolCallback> tools, String sessionId) {
        return tools.stream()
                .map(cb -> wrapWithPermission(cb, sessionId))
                .toList();
    }

    private ToolCallback wrapWithPermission(ToolCallback original, String sessionId) {
        var toolName = original.getToolDefinition().name();
        var meta = toolRegistry.listAll().stream()
                .filter(m -> m.name().equals(toolName))
                .findFirst()
                .orElse(null);

        // 无风险或未注册的工具不包装
        if (meta == null || meta.riskLevel() == ToolRiskLevel.NONE || meta.riskLevel() == ToolRiskLevel.LOW) {
            return original;
        }

        return new GuardedToolCallback(original, meta.riskLevel(), sessionId);
    }

    /** 带权限检查的 ToolCallback 装饰器 */
    private class GuardedToolCallback implements ToolCallback {
        private final ToolCallback delegate;
        private final ToolRiskLevel riskLevel;
        private final String sessionId;

        GuardedToolCallback(ToolCallback delegate, ToolRiskLevel riskLevel, String sessionId) {
            this.delegate = delegate;
            this.riskLevel = riskLevel;
            this.sessionId = sessionId;
        }

        @Override
        public ToolDefinition getToolDefinition() { return delegate.getToolDefinition(); }

        @Override
        public String call(String arguments) {
            var toolName = delegate.getToolDefinition().name();
            var userId = actorContext.currentUserId().orElse(null);
            var result = permissionChecker.check(sessionId, userId, toolName, riskLevel, false);

            return switch (result) {
                case GRANTED, AUTO_GRANTED -> delegate.call(arguments);
                case PENDING_APPROVAL -> "[权限等待] 工具 %s 需要用户确认，请稍后重试".formatted(toolName);
                case DENIED -> "[权限拒绝] 工具 %s 权限不足".formatted(toolName);
            };
        }
    }
}
