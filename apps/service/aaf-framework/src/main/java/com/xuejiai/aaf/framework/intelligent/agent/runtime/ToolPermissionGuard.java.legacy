package com.xuejiai.aaf.framework.intelligent.agent.runtime;

import java.util.List;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.enums.OverLimitAction;
import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.engine.credit.AiCreditGuard;
import com.xuejiai.aaf.framework.engine.tool.ToolCallDispatcher.ToolCallResult;
import com.xuejiai.aaf.framework.engine.tool.ToolCatalogEntry;
import com.xuejiai.aaf.framework.engine.tool.ToolCatalogProvider;
import com.xuejiai.aaf.framework.engine.tool.ToolPermissionChecker;
import com.xuejiai.aaf.framework.engine.tool.ToolRegistry;
import com.xuejiai.aaf.framework.engine.tool.ToolRiskLevel;
import com.xuejiai.aaf.framework.engine.tool.ToolType;
import com.xuejiai.aaf.framework.intelligent.ai.safety.ContentSafetyRequest;
import com.xuejiai.aaf.framework.intelligent.ai.safety.ContentSafetyService;
import com.xuejiai.aaf.framework.intelligent.assistant.AssistantPermissionEvaluator;
import com.xuejiai.aaf.framework.intelligent.assistant.hitl.HumanApprovalService;
import com.xuejiai.aaf.framework.intelligent.core.confidence.ConfidenceGate;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.framework.security.access.AccessDecisionService;

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
    private final ObjectProvider<ToolCatalogProvider> toolCatalogProvider;
    private final ObjectProvider<AiCreditGuard> creditService;
    private final ObjectProvider<ContentSafetyService> contentSafetyService;
    private final ObjectProvider<ConfidenceGate> confidenceGate;
    private final AccessDecisionService accessDecisionService;

    /**
     * 包装工具列表，为每个工具加上权限检查。
     *
     * @param tools 原始工具列表
     * @param sessionId 会话 ID
     * @param assistantId 助理标识（用于委托权限判定，可为 null）
     * @param agentAllowedTools Agent 预授权白名单（可为 null）
     */
    public List<ToolCallback> guard(
            List<ToolCallback> tools,
            String sessionId,
            String assistantId,
            List<String> agentAllowedTools) {
        return tools.stream()
                .map(cb -> wrapWithPermission(cb, sessionId, assistantId, agentAllowedTools))
                .toList();
    }

    /** 兼容旧接口。 */
    public List<ToolCallback> guard(
            List<ToolCallback> tools, String sessionId, List<String> agentAllowedTools) {
        return guard(tools, sessionId, null, agentAllowedTools);
    }

    /** 兼容旧接口。 */
    public List<ToolCallback> guard(List<ToolCallback> tools, String sessionId) {
        return guard(tools, sessionId, null, null);
    }

    private ToolCallback wrapWithPermission(
            ToolCallback original,
            String sessionId,
            String assistantId,
            List<String> agentAllowedTools) {
        var toolName = original.getToolDefinition().name();
        var meta =
                toolRegistry.listAll().stream()
                        .filter(m -> m.name().equals(toolName))
                        .findFirst()
                        .orElse(null);

        if (meta == null) {
            return new DeniedToolCallback(
                    original, "TOOL_NOT_REGISTERED", "工具未注册到治理目录: " + toolName);
        }

        var catalog = toolCatalogProvider.getIfAvailable();
        var entry = catalog == null ? null : catalog.find(toolName).orElse(null);
        if (catalog != null && (entry == null || !entry.enabled())) {
            return new DeniedToolCallback(original, "TOOL_DISABLED", "工具未开放或已禁用: " + toolName);
        }
        if (!hasToolPermission(toolName, entry)) {
            return new DeniedToolCallback(
                    original, "FORBIDDEN", "工具 %s 权限不足，需管理员授权".formatted(toolName));
        }
        var riskLevel =
                entry == null || entry.riskLevel() == null ? meta.riskLevel() : entry.riskLevel();
        var readOnly = entry == null ? meta.readOnly() : entry.readOnly();
        var requireConfirm = entry != null && entry.requireConfirm();
        return new GuardedToolCallback(
                original,
                entry,
                riskLevel,
                readOnly,
                requireConfirm,
                sessionId,
                assistantId,
                agentAllowedTools);
    }

    /** 被目录禁用的工具回调。 */
    private class DeniedToolCallback implements ToolCallback {
        private final ToolCallback delegate;
        private final String code;
        private final String reason;

        DeniedToolCallback(ToolCallback delegate, String code, String reason) {
            this.delegate = delegate;
            this.code = code;
            this.reason = reason;
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return delegate.getToolDefinition();
        }

        @Override
        public String call(String arguments) {
            if ("FORBIDDEN".equals(code)) {
                return asJson(
                        ToolCallResult.forbidden(delegate.getToolDefinition().name(), reason));
            }
            return asJson(ToolCallResult.error(delegate.getToolDefinition().name(), code, reason));
        }
    }

    /** 带权限检查的 ToolCallback 装饰器 */
    private class GuardedToolCallback implements ToolCallback {
        private final ToolCallback delegate;
        private final ToolCatalogEntry catalogEntry;
        private final ToolRiskLevel riskLevel;
        private final boolean readOnly;
        private final boolean requireConfirm;
        private final String sessionId;
        private final String assistantId;
        private final List<String> agentAllowedTools;

        GuardedToolCallback(
                ToolCallback delegate,
                ToolCatalogEntry catalogEntry,
                ToolRiskLevel riskLevel,
                boolean readOnly,
                boolean requireConfirm,
                String sessionId,
                String assistantId,
                List<String> agentAllowedTools) {
            this.delegate = delegate;
            this.catalogEntry = catalogEntry;
            this.riskLevel = riskLevel;
            this.readOnly = readOnly;
            this.requireConfirm = requireConfirm;
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
            var userId = operatorContext.currentUserId().orElse(null);

            // 先走委托模型判定（如果有 assistantId）
            if (assistantId != null) {
                var evalResult =
                        assistantPermEval.evaluateToolCall(
                                sessionId, assistantId, toolName, riskLevel);
                if (!evalResult.allowed()) {
                    return handleOverLimit(evalResult.action(), toolName, evalResult.reason());
                }
            }

            var confidenceBlock =
                    checkConfidence(
                            userId,
                            toolName,
                            arguments,
                            readOnly,
                            riskLevel,
                            sessionId,
                            agentAllowedTools);
            if (confidenceBlock != null) {
                return asJson(confidenceBlock);
            }

            // 再走已有的会话级权限检查
            var result =
                    permissionChecker.checkDetailed(
                            sessionId,
                            userId,
                            toolName,
                            riskLevel,
                            readOnly,
                            requireConfirm,
                            agentAllowedTools,
                            arguments);

            return switch (result.result()) {
                case GRANTED, AUTO_GRANTED -> {
                    var safetyBlock =
                            checkContentSafety(
                                    sessionId, userId, toolName, catalogEntry, arguments);
                    if (safetyBlock != null) {
                        yield asJson(safetyBlock);
                    }
                    var creditBlock = checkCredit(userId, toolName, catalogEntry);
                    if (creditBlock != null) {
                        yield asJson(creditBlock);
                    }
                    String output;
                    try {
                        output = delegate.call(arguments);
                    } catch (com.xuejiai.aaf.common.exception.InsufficientCreditsException e) {
                        // 积分不足时转结构化结果，供 LLM 感知并告知用户
                        yield asJson(ToolCallResult.insufficientCredits(toolName, "ai_credit", 0));
                    }
                    if (isSuccessfulOutput(output)) {
                        settleCredit(userId, toolName, catalogEntry);
                    }
                    yield output;
                }
                case PENDING_APPROVAL ->
                        asJson(
                                ToolCallResult.pendingApproval(
                                        toolName,
                                        "工具 %s 需要用户确认，请等待确认后恢复执行".formatted(toolName),
                                        result.approvalId()));
                case DENIED ->
                        asJson(
                                ToolCallResult.forbidden(
                                        toolName, "工具 %s 权限不足，需管理员授权".formatted(toolName)));
            };
        }

        private String handleOverLimit(OverLimitAction action, String toolName, String reason) {
            return switch (action) {
                case ASK -> {
                    // 触发 HITL 审批流程
                    var userId = operatorContext.currentUserId().orElse(null);
                    var decision =
                            permissionChecker.checkDetailed(
                                    sessionId,
                                    userId,
                                    toolName,
                                    riskLevel,
                                    readOnly,
                                    true,
                                    agentAllowedTools,
                                    null);
                    yield asJson(
                            ToolCallResult.pendingApproval(
                                    toolName,
                                    "%s — %s".formatted(toolName, reason),
                                    decision.approvalId()));
                }
                case SKIP ->
                        asJson(
                                ToolCallResult.error(
                                        toolName,
                                        "SKIPPED",
                                        "%s — %s".formatted(toolName, reason)));
                case PAUSE ->
                        asJson(
                                ToolCallResult.pendingApproval(
                                        toolName,
                                        "%s — %s，等待用户介入".formatted(toolName, reason),
                                        null));
            };
        }
    }

    private String asJson(ToolCallResult result) {
        try {
            return JsonUtils.toJsonString(result);
        } catch (Exception ex) {
            return "{\"success\":false,\"code\":\"TOOL_RESULT_SERIALIZE_ERROR\",\"message\":\"工具结果序列化失败\"}";
        }
    }

    private boolean hasToolPermission(String toolName, ToolCatalogEntry entry) {
        var permissionCode = entry == null ? null : entry.permissionCode();
        if (permissionCode == null || permissionCode.isBlank()) {
            permissionCode = "tool:%s:execute".formatted(normalizePermissionSegment(toolName));
        }
        return accessDecisionService.hasPermission(permissionCode)
                || accessDecisionService.hasPermission("tool:default:execute");
    }

    private String normalizePermissionSegment(String value) {
        return value == null ? "" : value.trim().toLowerCase().replace('_', '-');
    }

    private ToolCallResult checkContentSafety(
            String sessionId,
            Long userId,
            String toolName,
            ToolCatalogEntry entry,
            String arguments) {
        if (entry == null || entry.type() != ToolType.GENERATIVE) {
            return null;
        }
        var prompt = extractPrompt(arguments);
        if (prompt == null || prompt.isBlank()) {
            return ToolCallResult.error(
                    toolName, "CONTENT_REVIEW_INPUT_MISSING", "生成式工具缺少 prompt，无法进行内容审查");
        }
        var safety = contentSafetyService.getIfAvailable();
        if (safety == null) {
            return null;
        }
        var result =
                safety.reviewBeforeGeneration(
                        new ContentSafetyRequest(
                                toolName,
                                entry.category(),
                                sessionId,
                                userId,
                                prompt,
                                java.util.Map.of(
                                        "toolType",
                                        entry.type().name(),
                                        "riskLevel",
                                        entry.riskLevel() == null ? "" : entry.riskLevel().name(),
                                        "requireHumanReview",
                                        entry.riskLevel() == ToolRiskLevel.HIGH
                                                || entry.riskLevel() == ToolRiskLevel.CRITICAL)));
        if (result.allowed()) {
            return null;
        }
        if (result.reviewRequired()) {
            return ToolCallResult.pendingContentReview(
                    toolName, result.message(), result.reviewId());
        }
        return ToolCallResult.error(toolName, result.code(), result.message());
    }

    private ToolCallResult checkConfidence(
            Long userId,
            String toolName,
            String arguments,
            boolean readOnly,
            ToolRiskLevel riskLevel,
            String sessionId,
            List<String> agentAllowedTools) {
        var gate = confidenceGate.getIfAvailable();
        if (gate == null) {
            return null;
        }
        var confidence = extractDouble(arguments, "confidence");
        if (confidence == null) {
            return null;
        }
        var verifiable = extractBoolean(arguments, "verifiable", readOnly);
        var decision =
                gate.evaluate(
                        new ConfidenceGate.GateInput(
                                confidence, verifiable, "tool:%s".formatted(toolName)));
        if (decision.action() != ConfidenceGate.Action.PAUSE_FOR_HUMAN) {
            return null;
        }
        var approval =
                permissionChecker.checkDetailed(
                        sessionId,
                        userId,
                        "confidence:" + toolName,
                        riskLevel,
                        readOnly,
                        true,
                        agentAllowedTools,
                        arguments,
                        HumanApprovalService.ApprovalType.LOW_CONFIDENCE,
                        "置信度门控确认",
                        decision.message() == null ? "工具调用置信度不足" : decision.message());
        return switch (approval.result()) {
            case GRANTED, AUTO_GRANTED -> null;
            case PENDING_APPROVAL ->
                    ToolCallResult.pendingApproval(
                            toolName,
                            decision.message() == null ? "工具调用置信度不足，需要用户确认" : decision.message(),
                            approval.approvalId());
            case DENIED -> ToolCallResult.forbidden(toolName, "工具调用置信度不足，且当前会话已拒绝该工具");
        };
    }

    private ToolCallResult checkCredit(Long userId, String toolName, ToolCatalogEntry entry) {
        var credit = creditService.getIfAvailable();
        if (credit == null
                || userId == null
                || entry == null
                || entry.entitlementCode() == null
                || entry.entitlementCode().isBlank()) {
            return null;
        }
        var cost = estimateCost(entry.costExpression());
        if (cost <= 0 || credit.hasBudget(userId, cost)) {
            return null;
        }
        return ToolCallResult.insufficientCredits(toolName, entry.entitlementCode(), cost);
    }

    private void settleCredit(Long userId, String toolName, ToolCatalogEntry entry) {
        var credit = creditService.getIfAvailable();
        if (credit == null
                || userId == null
                || entry == null
                || entry.entitlementCode() == null
                || entry.entitlementCode().isBlank()) {
            return;
        }
        var cost = estimateCost(entry.costExpression());
        if (cost <= 0) {
            return;
        }
        try {
            credit.settleFixed(
                    userId,
                    cost,
                    entry.entitlementCode() != null ? entry.entitlementCode() : "tool");
        } catch (Exception ex) {
            log.warn(
                    "工具扣费失败，已完成调用不回滚: tool={}, userId={}, cost={}, err={}",
                    toolName,
                    userId,
                    cost,
                    ex.getMessage());
        }
    }

    private long estimateCost(String expression) {
        if (expression == null || expression.isBlank()) {
            return 0;
        }
        try {
            return Long.parseLong(expression.trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private boolean isSuccessfulOutput(String output) {
        if (output == null || output.isBlank()) {
            return true;
        }
        try {
            var node = JsonUtils.readTree(output);
            return !node.has("success") || node.get("success").asBoolean();
        } catch (Exception ignored) {
            return true;
        }
    }

    private String extractPrompt(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return null;
        }
        try {
            var node = JsonUtils.readTree(arguments);
            if (node.hasNonNull("prompt")) {
                return node.get("prompt").asText();
            }
            if (node.hasNonNull("requestJson")) {
                var nested = JsonUtils.readTree(node.get("requestJson").asText());
                return nested.hasNonNull("prompt") ? nested.get("prompt").asText() : null;
            }
        } catch (Exception ignored) {
            // 非 JSON 参数无法抽取 prompt。
        }
        return null;
    }

    private Double extractDouble(String arguments, String field) {
        if (arguments == null || arguments.isBlank()) {
            return null;
        }
        try {
            var node = JsonUtils.readTree(arguments);
            if (node.hasNonNull(field) && node.get(field).isNumber()) {
                return node.get(field).asDouble();
            }
            if (node.hasNonNull("requestJson")) {
                var nested = JsonUtils.readTree(node.get("requestJson").asText());
                return nested.hasNonNull(field) && nested.get(field).isNumber()
                        ? nested.get(field).asDouble()
                        : null;
            }
        } catch (Exception ignored) {
            // 非 JSON 参数无法抽取置信度。
        }
        return null;
    }

    private boolean extractBoolean(String arguments, String field, boolean defaultValue) {
        if (arguments == null || arguments.isBlank()) {
            return defaultValue;
        }
        try {
            var node = JsonUtils.readTree(arguments);
            if (node.hasNonNull(field) && node.get(field).isBoolean()) {
                return node.get(field).asBoolean();
            }
            if (node.hasNonNull("requestJson")) {
                var nested = JsonUtils.readTree(node.get("requestJson").asText());
                return nested.hasNonNull(field) && nested.get(field).isBoolean()
                        ? nested.get(field).asBoolean()
                        : defaultValue;
            }
        } catch (Exception ignored) {
            // 非 JSON 参数无法抽取可验证性。
        }
        return defaultValue;
    }
}
