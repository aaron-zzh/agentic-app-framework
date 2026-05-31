package com.xuejiai.aaf.framework.intelligent.agent;

import java.util.List;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.enums.OverLimitAction;
import com.xuejiai.aaf.framework.engine.tool.ToolPermissionChecker;
import com.xuejiai.aaf.framework.engine.tool.ToolRegistry;
import com.xuejiai.aaf.framework.engine.tool.ToolRiskLevel;
import com.xuejiai.aaf.framework.intelligent.assistant.AssistantPermissionEvaluator;
import com.xuejiai.aaf.framework.security.OperatorContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 工具权限拦截——通过装饰 ToolCallback 实现 Agent 内部工具调用的权限检查。
 *
 * <p>组合模式：包装原始 ToolCallback，在 call() 前执行权限检查。 这样无论 AgentScope 内部如何调用工具，都会经过权限拦截。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolPermissionGuard {

    private final ToolPermissionChecker permissionChecker;
    private final ToolRegistry toolRegistry;
    private final OperatorContext operatorContext;
    private final AssistantPermissionEvaluator assistantPermEval;

    /**
     * 包装工具列表，为每个工具加上权限检查。
     *
     * @param tools 原始工具列表
     * @param sessionId 会话 ID
     * @param assistantId 助理标识（用于委托权限判定，可为 null）
     * @param agentAllowedTools Agent 预授权白名单（可为 null）
     */
    public List<ToolCallback> guard(
            List<ToolCallback> tools, String sessionId, String assistantId, List<String> agentAllowedTools) {
        return tools.stream()
                .map(cb -> wrapWithPermission(cb, sessionId, assistantId, agentAllowedTools))
                .toList();
    }

    /** 兼容旧接口。 */
    public List<ToolCallback> guard(List<ToolCallback> tools, String sessionId, List<String> agentAllowedTools) {
        return guard(tools, sessionId, null, agentAllowedTools);
    }

    /** 兼容旧接口。 */
    public List<ToolCallback> guard(List<ToolCallback> tools, String sessionId) {
        return guard(tools, sessionId, null, null);
    }

    private ToolCallback wrapWithPermission(
            ToolCallback original, String sessionId, String assistantId, List<String> agentAllowedTools) {
        var toolName = original.getToolDefinition().name();
        var meta =
                toolRegistry.listAll().stream()
                        .filter(m -> m.name().equals(toolName))
                        .findFirst()
                        .orElse(null);

        // 无元数据的工具不包装（默认放行）
        if (meta == null) {
            return original;
        }

        return new GuardedToolCallback(
                original, meta.riskLevel(), meta.readOnly(), sessionId, assistantId, agentAllowedTools);
    }

    /** 带权限检查的 ToolCallback 装饰器 */
    private class GuardedToolCallback implements ToolCallback {
        private final ToolCallback delegate;
        private final ToolRiskLevel riskLevel;
        private final boolean readOnly;
        private final String sessionId;
        private final String assistantId;
        private final List<String> agentAllowedTools;

        GuardedToolCallback(
                ToolCallback delegate,
                ToolRiskLevel riskLevel,
                boolean readOnly,
                String sessionId,
                String assistantId,
                List<String> agentAllowedTools) {
            this.delegate = delegate;
            this.riskLevel = riskLevel;
            this.readOnly = readOnly;
            this.sessionId = sessionId;
            this.assistantId = assistantId;
            this.agentAllowedTools = agentAllowedTools;
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return delegate.getToolDefinition();
        }

        @Override
        public String call(String arguments) {
            var toolName = delegate.getToolDefinition().name();

            // 先走委托模型判定（如果有 assistantId）
            if (assistantId != null) {
                var evalResult = assistantPermEval.evaluateToolCall(sessionId, assistantId, toolName, riskLevel);
                if (!evalResult.allowed()) {
                    return handleOverLimit(evalResult.action(), toolName, evalResult.reason());
                }
            }

            // 再走已有的会话级权限检查
            var userId = operatorContext.currentUserId().orElse(null);
            var result =
                    permissionChecker.check(
                            sessionId,
                            userId,
                            toolName,
                            riskLevel,
                            readOnly,
                            agentAllowedTools,
                            arguments);

            return switch (result) {
                case GRANTED, AUTO_GRANTED -> delegate.call(arguments);
                case PENDING_APPROVAL -> "[权限等待] 工具 %s 需要用户确认，请稍后重试".formatted(toolName);
                case DENIED -> "[权限拒绝] 工具 %s 权限不足".formatted(toolName);
            };
        }

        private String handleOverLimit(OverLimitAction action, String toolName, String reason) {
            return switch (action) {
                case ASK -> {
                    // 触发 HITL 审批流程
                    var userId = operatorContext.currentUserId().orElse(null);
                    permissionChecker.check(
                            sessionId, userId, toolName, riskLevel, readOnly, agentAllowedTools, null);
                    yield "[权限等待] %s — %s".formatted(toolName, reason);
                }
                case SKIP -> "[已跳过] %s — %s".formatted(toolName, reason);
                case PAUSE -> "[任务暂停] %s — %s，等待用户介入".formatted(toolName, reason);
            };
        }
    }
}
