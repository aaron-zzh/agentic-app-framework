package com.xuejiai.aaf.framework.engine.tool;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.engine.credit.AiCreditGuard;
import com.xuejiai.aaf.framework.intelligent.ai.safety.ContentSafetyRequest;
import com.xuejiai.aaf.framework.intelligent.ai.safety.ContentSafetyService;
import com.xuejiai.aaf.framework.intelligent.assistant.hitl.HumanApprovalService;
import com.xuejiai.aaf.framework.intelligent.core.confidence.ConfidenceGate;
import com.xuejiai.aaf.framework.security.access.AccessDecisionService;

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
    private final ToolCallAuditRepository auditRepository;
    private final ObjectProvider<ToolCatalogProvider> catalogProvider;
    private final AccessDecisionService accessDecisionService;
    private final ObjectProvider<AiCreditGuard> creditService;
    private final ObjectProvider<ContentSafetyService> contentSafetyService;
    private final ObjectProvider<ConfidenceGate> confidenceGate;

    /** 执行工具调用（无权限检查，Agent 内部同包调用）。 */
    ToolCallResult dispatch(String functionName, String arguments) {
        return doDispatch(functionName, arguments);
    }

    /** 执行工具调用（含权限检查，外部调用）。 */
    public ToolCallResult dispatchWithPermission(
            String sessionId, Long userId, Long roleId, String functionName, String arguments) {
        var meta =
                registry.listAll().stream()
                        .filter(m -> m.name().equals(functionName))
                        .findFirst()
                        .orElse(null);
        if (meta == null) {
            return ToolCallResult.error(
                    functionName, "TOOL_NOT_REGISTERED", "工具未注册: " + functionName);
        }
        var entry = catalogEntry(functionName);
        var effectiveMeta = applyCatalog(functionName, meta, entry);
        if (effectiveMeta == null) {
            return ToolCallResult.error(
                    functionName, "TOOL_DISABLED", "工具未启用或未开放: " + functionName);
        }

        var hasRolePermission =
                registry.resolveForRole(roleId).stream()
                        .anyMatch(cb -> cb.getToolDefinition().name().equals(functionName));
        if (!hasToolPermission(effectiveMeta, entry)) {
            return ToolCallResult.forbidden(
                    functionName, "工具 [%s] 权限不足，需管理员授予工具权限".formatted(functionName));
        }
        var confidenceBlock =
                checkConfidence(sessionId, userId, functionName, effectiveMeta, arguments);
        if (confidenceBlock != null) {
            return confidenceBlock;
        }
        var permission =
                permissionChecker.checkDetailed(
                        sessionId,
                        userId,
                        functionName,
                        effectiveMeta.riskLevel(),
                        effectiveMeta.readOnly(),
                        entry != null && entry.requireConfirm(),
                        hasRolePermission ? List.of(functionName) : null,
                        arguments);

        return switch (permission.result()) {
            case GRANTED, AUTO_GRANTED -> {
                var safetyBlock =
                        checkContentSafety(sessionId, userId, functionName, entry, arguments);
                if (safetyBlock != null) {
                    yield safetyBlock;
                }
                var creditBlock = checkCredit(userId, functionName, entry);
                if (creditBlock != null) {
                    yield creditBlock;
                }
                yield doDispatch(functionName, arguments, userId, entry);
            }
            case PENDING_APPROVAL ->
                    ToolCallResult.pendingApproval(
                            functionName,
                            "工具 [%s] 需要用户确认（风险等级: %s）"
                                    .formatted(functionName, effectiveMeta.riskLevel()),
                            permission.approvalId());
            case DENIED ->
                    ToolCallResult.forbidden(
                            functionName, "工具 [%s] 权限不足，需管理员审批".formatted(functionName));
        };
    }

    private ToolCatalogEntry catalogEntry(String functionName) {
        var provider = catalogProvider.getIfAvailable();
        return provider == null ? null : provider.find(functionName).orElse(null);
    }

    private ToolRegistry.ToolMeta applyCatalog(
            String functionName, ToolRegistry.ToolMeta meta, ToolCatalogEntry entry) {
        if (catalogProvider.getIfAvailable() != null && (entry == null || !entry.enabled())) {
            return null;
        }
        return new ToolRegistry.ToolMeta(
                meta.name(),
                meta.description(),
                meta.source(),
                entry == null || entry.type() == null ? meta.type() : entry.type(),
                entry == null || entry.riskLevel() == null ? meta.riskLevel() : entry.riskLevel(),
                entry == null ? meta.readOnly() : entry.readOnly(),
                entry == null || entry.inputSchema() == null || entry.inputSchema().isBlank()
                        ? meta.parametersSchema()
                        : entry.inputSchema());
    }

    private ToolCallResult checkContentSafety(
            String sessionId,
            Long userId,
            String functionName,
            ToolCatalogEntry entry,
            String arguments) {
        if (entry == null || entry.type() != ToolType.GENERATIVE) {
            return null;
        }
        var prompt = extractPrompt(arguments);
        if (prompt == null || prompt.isBlank()) {
            return ToolCallResult.error(
                    functionName, "CONTENT_REVIEW_INPUT_MISSING", "生成式工具缺少 prompt，无法进行内容审查");
        }
        var safety = contentSafetyService.getIfAvailable();
        if (safety == null) {
            return null;
        }
        var result =
                safety.reviewBeforeGeneration(
                        new ContentSafetyRequest(
                                functionName,
                                entry.category(),
                                sessionId,
                                userId,
                                prompt,
                                Map.of(
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
                    functionName, result.message(), result.reviewId());
        }
        return ToolCallResult.error(functionName, result.code(), result.message());
    }

    private ToolCallResult checkConfidence(
            String sessionId,
            Long userId,
            String functionName,
            ToolRegistry.ToolMeta meta,
            String arguments) {
        var gate = confidenceGate.getIfAvailable();
        if (gate == null) {
            return null;
        }
        var confidence = extractDouble(arguments, "confidence");
        if (confidence == null) {
            return null;
        }
        var verifiable = extractBoolean(arguments, "verifiable", meta.readOnly());
        var decision =
                gate.evaluate(
                        new ConfidenceGate.GateInput(
                                confidence, verifiable, "tool:%s".formatted(functionName)));
        if (decision.action() != ConfidenceGate.Action.PAUSE_FOR_HUMAN) {
            return null;
        }
        var approval =
                permissionChecker.checkDetailed(
                        sessionId,
                        userId,
                        "confidence:" + functionName,
                        meta.riskLevel(),
                        meta.readOnly(),
                        true,
                        null,
                        arguments,
                        HumanApprovalService.ApprovalType.LOW_CONFIDENCE,
                        "置信度门控确认",
                        decision.message() == null ? "工具调用置信度不足" : decision.message());
        return switch (approval.result()) {
            case GRANTED, AUTO_GRANTED -> null;
            case PENDING_APPROVAL ->
                    ToolCallResult.pendingApproval(
                            functionName,
                            decision.message() == null ? "工具调用置信度不足，需要用户确认" : decision.message(),
                            approval.approvalId());
            case DENIED -> ToolCallResult.forbidden(functionName, "工具调用置信度不足，且当前会话已拒绝该工具");
        };
    }

    private ToolCallResult checkCredit(Long userId, String functionName, ToolCatalogEntry entry) {
        var credit = creditService.getIfAvailable();
        if (credit == null || userId == null) {
            return null;
        }
        if (entry == null || entry.entitlementCode() == null || entry.entitlementCode().isBlank()) {
            return null;
        }
        var cost = estimateCost(entry.costExpression());
        if (cost <= 0 || credit.hasBudget(userId, cost)) {
            return null;
        }
        return ToolCallResult.insufficientCredits(functionName, entry.entitlementCode(), cost);
    }

    private void settleCredit(Long userId, String functionName, ToolCatalogEntry entry) {
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
                    functionName,
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

    private boolean hasToolPermission(ToolRegistry.ToolMeta meta, ToolCatalogEntry entry) {
        var permissionCode = entry == null ? null : entry.permissionCode();
        if (permissionCode == null || permissionCode.isBlank()) {
            permissionCode = "tool:%s:execute".formatted(normalizePermissionSegment(meta.name()));
        }
        return accessDecisionService.hasPermission(permissionCode)
                || accessDecisionService.hasPermission("tool:default:execute");
    }

    private String normalizePermissionSegment(String value) {
        return value == null ? "" : value.trim().toLowerCase().replace('_', '-');
    }

    private ToolCallResult doDispatch(String functionName, String arguments) {
        return doDispatch(functionName, arguments, null, null);
    }

    private ToolCallResult doDispatch(
            String functionName, String arguments, Long userId, ToolCatalogEntry entry) {
        var callback = registry.getCallback(functionName).orElse(null);
        if (callback == null) {
            return ToolCallResult.error(
                    functionName, "TOOL_NOT_REGISTERED", "工具未注册: " + functionName);
        }
        var start = System.currentTimeMillis();
        try {
            var result = callback.call(arguments);
            var duration = System.currentTimeMillis() - start;
            var normalized = normalizeCallbackResult(functionName, result);
            log.debug("工具调用完成: {} -> {} ({}ms)", functionName, truncate(result), duration);
            saveAudit(
                    functionName,
                    arguments,
                    normalized.success(),
                    result,
                    normalized.error(),
                    duration);
            if (normalized.success()) {
                settleCredit(userId, functionName, entry);
            }
            return normalized;
        } catch (Exception e) {
            var duration = System.currentTimeMillis() - start;
            log.warn("工具调用失败: {} - {} ({}ms)", functionName, e.getMessage(), duration);
            saveAudit(functionName, arguments, false, null, e.getMessage(), duration);
            return ToolCallResult.error(functionName, "TOOL_EXECUTION_ERROR", e.getMessage());
        }
    }

    private void saveAudit(
            String functionName,
            String arguments,
            boolean success,
            String output,
            String error,
            long durationMs) {
        try {
            var audit = new ToolCallAudit();
            audit.setFunctionName(functionName);
            audit.setArguments(arguments);
            audit.setSuccess(success);
            audit.setOutput(output != null ? truncate(output) : null);
            audit.setErrorMessage(error);
            audit.setDurationMs(durationMs);
            auditRepository.save(audit);
        } catch (Exception e) {
            log.warn("审计记录写入失败: {}", e.getMessage());
        }
    }

    private String truncate(String s) {
        return s != null && s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }

    private ToolCallResult normalizeCallbackResult(String functionName, String result) {
        if (result == null || result.isBlank()) {
            return ToolCallResult.success(functionName, result);
        }
        try {
            var node = JsonUtils.readTree(result);
            if (node.has("success") && node.has("code") && node.has("message")) {
                return JsonUtils.convertValue(node, ToolCallResult.class);
            }
        } catch (Exception ignored) {
            // 普通文本结果按成功处理。
        }
        return ToolCallResult.success(functionName, result);
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

    /** 工具调用结果 */
    public record ToolCallResult(
            String functionName,
            boolean success,
            String code,
            String message,
            String output,
            String error,
            boolean pendingApproval,
            boolean recoverable,
            Authorization authorization,
            Resume resume,
            Map<String, Object> data) {
        public static ToolCallResult success(String name, String output) {
            return new ToolCallResult(
                    name, true, "OK", "执行成功", output, null, false, false, null, null, Map.of());
        }

        public static ToolCallResult error(String name, String code, String message) {
            return new ToolCallResult(
                    name, false, code, message, null, message, false, false, null, null, Map.of());
        }

        public static ToolCallResult forbidden(String name, String message) {
            return new ToolCallResult(
                    name,
                    false,
                    "FORBIDDEN",
                    message,
                    null,
                    message,
                    false,
                    true,
                    new Authorization("ADMIN_REQUIRED", null, "管理员授权"),
                    null,
                    Map.of());
        }

        public static ToolCallResult pendingApproval(
                String name, String message, String approvalId) {
            return new ToolCallResult(
                    name,
                    false,
                    "PENDING_APPROVAL",
                    message,
                    null,
                    null,
                    true,
                    true,
                    new Authorization("USER_APPROVAL", approvalId, "用户即时确认"),
                    new Resume("WAIT_APPROVAL", approvalId, "用户确认后使用相同参数重试"),
                    Map.of());
        }

        public static ToolCallResult pendingContentReview(
                String name, String message, String reviewId) {
            return new ToolCallResult(
                    name,
                    false,
                    "PENDING_CONTENT_REVIEW",
                    message,
                    null,
                    null,
                    true,
                    true,
                    new Authorization("CONTENT_REVIEW", reviewId, "内容安全审查"),
                    new Resume("WAIT_CONTENT_REVIEW", reviewId, "审查通过后使用相同参数重试"),
                    Map.of());
        }

        public static ToolCallResult insufficientCredits(
                String name, String entitlementCode, long estimatedCost) {
            return new ToolCallResult(
                    name,
                    false,
                    "INSUFFICIENT_CREDITS",
                    "积分或额度不足，请充值或升级后恢复执行",
                    null,
                    "积分或额度不足",
                    false,
                    true,
                    new Authorization("BILLING_REQUIRED", null, "用户充值/升级"),
                    new Resume("WAIT_CREDIT", entitlementCode, "额度恢复后使用相同参数重试"),
                    Map.of("entitlementCode", entitlementCode, "estimatedCost", estimatedCost));
        }
    }

    /** 授权恢复信息。 */
    public record Authorization(String mode, String approvalId, String requiredBy) {}

    /** 阻塞后的恢复执行提示。 */
    public record Resume(String strategy, String token, String instruction) {}
}
