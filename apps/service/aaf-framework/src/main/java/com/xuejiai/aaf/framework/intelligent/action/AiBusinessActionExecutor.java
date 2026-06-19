package com.xuejiai.aaf.framework.intelligent.action;

import org.springframework.stereotype.Service;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.engine.credit.AiCreditGuard;
import com.xuejiai.aaf.framework.engine.tool.ToolPermissionChecker;
import com.xuejiai.aaf.framework.engine.tool.ToolRiskLevel;
import com.xuejiai.aaf.framework.intelligent.assistant.hitl.HumanApprovalService;
import com.xuejiai.aaf.framework.intelligent.core.confidence.ConfidenceGate;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.framework.security.PermissionExecutionService;
import com.xuejiai.aaf.framework.security.access.AccessDecisionService;

import lombok.RequiredArgsConstructor;

/** AI 业务动作统一执行器。 */
@Service
@RequiredArgsConstructor
public class AiBusinessActionExecutor {

    private final EntityActionRegistry registry;
    private final AccessDecisionService accessDecisionService;
    private final PermissionExecutionService permissionExecutionService;
    private final ToolPermissionChecker permissionChecker;
    private final OperatorContext operatorContext;
    private final org.springframework.beans.factory.ObjectProvider<AiCreditGuard> creditService;
    private final org.springframework.beans.factory.ObjectProvider<ConfidenceGate> confidenceGate;

    public AiBusinessActionResult execute(AiBusinessActionRequest request) {
        return doExecute(request);
    }

    public AiBusinessActionResult executeAsOwner(
            Long ownerId, String reason, AiBusinessActionRequest request) {
        return permissionExecutionService.runAsOwner(ownerId, reason, () -> doExecute(request));
    }

    private AiBusinessActionResult doExecute(AiBusinessActionRequest request) {
        if (request == null) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "AI 业务动作请求不能为空");
        }
        var action = AiBusinessActionType.from(request.action());
        var adapter = registry.getRequired(request.entity());
        if (!registry.isActionEnabled(adapter, action)) {
            return AiBusinessActionResult.failure(
                    "UNSUPPORTED_ACTION", "实体不支持动作: " + action.action());
        }
        var permissionCode = registry.permissionCode(adapter, action);
        if (permissionCode == null
                || permissionCode.isBlank()
                || !accessDecisionService.hasPermission(permissionCode)) {
            return AiBusinessActionResult.forbidden("权限不足: " + permissionCode);
        }
        var entry = registry.catalogEntry(adapter, action);
        var confidenceDecision = checkConfidence(request, adapter, action, entry);
        if (confidenceDecision != null) {
            return confidenceDecision;
        }
        var riskDecision = checkRisk(request, adapter, action, entry);
        if (riskDecision != null) {
            return riskDecision;
        }
        var creditBlock = checkCredit(adapter, action, entry);
        if (creditBlock != null) {
            return creditBlock;
        }
        var result = adapter.execute(action, request.params());
        settleCredit(adapter, action, entry);
        return AiBusinessActionResult.success(result);
    }

    private AiBusinessActionResult checkRisk(
            AiBusinessActionRequest request,
            EntityActionAdapter adapter,
            AiBusinessActionType action,
            AiActionCatalogEntry entry) {
        if (entry == null || !entry.requireConfirm()) {
            return null;
        }
        var userId = operatorContext.currentOwnerId().orElse(null);
        var decision =
                permissionChecker.checkDetailed(
                        request.sessionId(),
                        userId,
                        "%s.%s".formatted(adapter.entitySlug(), action.action()),
                        riskLevel(entry.riskLevel()),
                        false,
                        true,
                        null,
                        request.params() == null ? null : request.params().toString(),
                        HumanApprovalService.ApprovalType.ACTION_CONFIRM,
                        "业务动作确认",
                        "业务动作风险策略要求确认");
        return switch (decision.result()) {
            case GRANTED, AUTO_GRANTED -> null;
            case PENDING_APPROVAL ->
                    AiBusinessActionResult.pendingApproval(
                            "业务动作 [%s.%s] 需要用户确认".formatted(adapter.entitySlug(), action.action()),
                            decision.approvalId());
            case DENIED ->
                    AiBusinessActionResult.forbidden(
                            "业务动作 [%s.%s] 权限不足，需管理员授权"
                                    .formatted(adapter.entitySlug(), action.action()));
        };
    }

    private AiBusinessActionResult checkConfidence(
            AiBusinessActionRequest request,
            EntityActionAdapter adapter,
            AiBusinessActionType action,
            AiActionCatalogEntry entry) {
        var gate = confidenceGate.getIfAvailable();
        if (gate == null || request.confidence() == null) {
            return null;
        }
        var verifiable = request.verifiable() != null ? request.verifiable() : isReadAction(action);
        var decision =
                gate.evaluate(
                        new ConfidenceGate.GateInput(
                                request.confidence(),
                                verifiable,
                                "action:%s.%s".formatted(adapter.entitySlug(), action.action())));
        if (decision.action() != ConfidenceGate.Action.PAUSE_FOR_HUMAN) {
            return null;
        }
        var userId = operatorContext.currentOwnerId().orElse(null);
        var approval =
                permissionChecker.checkDetailed(
                        request.sessionId(),
                        userId,
                        "confidence:%s.%s".formatted(adapter.entitySlug(), action.action()),
                        entry == null ? ToolRiskLevel.MEDIUM : riskLevel(entry.riskLevel()),
                        false,
                        true,
                        null,
                        request.params() == null ? null : request.params().toString(),
                        HumanApprovalService.ApprovalType.LOW_CONFIDENCE,
                        "业务动作置信度确认",
                        decision.message() == null ? "业务动作置信度不足" : decision.message());
        return switch (approval.result()) {
            case GRANTED, AUTO_GRANTED -> null;
            case PENDING_APPROVAL ->
                    AiBusinessActionResult.pendingApproval(
                            decision.message() == null ? "业务动作置信度不足，需要用户确认" : decision.message(),
                            approval.approvalId());
            case DENIED -> AiBusinessActionResult.forbidden("业务动作置信度不足，且当前会话已拒绝该动作");
        };
    }

    private AiBusinessActionResult checkCredit(
            EntityActionAdapter adapter, AiBusinessActionType action, AiActionCatalogEntry entry) {
        var credit = creditService.getIfAvailable();
        var userId = operatorContext.currentOwnerId().orElse(null);
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
        return AiBusinessActionResult.insufficientCredits(
                "业务动作 [%s.%s] 积分或额度不足".formatted(adapter.entitySlug(), action.action()),
                entry.entitlementCode(),
                cost);
    }

    private void settleCredit(
            EntityActionAdapter adapter, AiBusinessActionType action, AiActionCatalogEntry entry) {
        var credit = creditService.getIfAvailable();
        var userId = operatorContext.currentOwnerId().orElse(null);
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
        credit.settleFixed(
                userId, cost, entry.entitlementCode() != null ? entry.entitlementCode() : "action");
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

    private ToolRiskLevel riskLevel(String value) {
        if (value == null || value.isBlank()) {
            return ToolRiskLevel.LOW;
        }
        try {
            return ToolRiskLevel.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return ToolRiskLevel.LOW;
        }
    }

    private boolean isReadAction(AiBusinessActionType action) {
        return switch (action) {
            case QUERY, DETAIL, BATCH_READ, OPTIONS, META -> true;
            case CREATE, UPDATE, DELETE, BATCH_DELETE, EXPORT, VALIDATE, ARCHIVE, RESTORE -> false;
        };
    }
}
