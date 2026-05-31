package com.xuejiai.aaf.framework.intelligent.assistant;

import java.util.Optional;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.enums.OverLimitAction;
import com.xuejiai.aaf.common.enums.RiskLevel;
import com.xuejiai.aaf.framework.engine.tool.ToolRiskLevel;
import com.xuejiai.aaf.framework.security.access.FunctionPermissionChecker;
import com.xuejiai.aaf.framework.security.access.RelationPermissionChecker;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * AI 助理权限评估器——实现"委托者权限 ∩ scope 白名单"的交集判定。
 *
 * <p>判定流程：
 * <ol>
 *   <li>查 AssistantDefinition → 获取 delegatorId + permissionScope</li>
 *   <li>检查操作是否在 scope 白名单内</li>
 *   <li>检查风险等级是否在 maxAutoRiskLevel 内</li>
 *   <li>不在 → 按 overLimitAction 返回处理策略</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AssistantPermissionEvaluator {

    private final AssistantDefinitionRepository assistantRepo;
    private final ObjectProvider<FunctionPermissionChecker> functionPermissionChecker;
    private final ObjectProvider<RelationPermissionChecker> relationPermissionChecker;
    private final AssistantSessionTrustService sessionTrustService;

    /** 评估结果 */
    public record EvalResult(
            boolean allowed,
            OverLimitAction action,
            String reason,
            Long delegatorId) {

        public static EvalResult granted(Long delegatorId) {
            return new EvalResult(true, null, null, delegatorId);
        }

        public static EvalResult denied(OverLimitAction action, String reason, Long delegatorId) {
            return new EvalResult(false, action, reason, delegatorId);
        }
    }

    /**
     * 评估助理是否有权执行指定工具调用。
     *
     * @param assistantId 助理标识
     * @param toolName 工具名
     * @param toolRiskLevel 工具风险等级
     * @return 评估结果
     */
    public EvalResult evaluateToolCall(String assistantId, String toolName, ToolRiskLevel toolRiskLevel) {
        return evaluateToolCall(null, assistantId, toolName, toolRiskLevel);
    }

    /**
     * 评估助理是否有权执行指定工具调用，带会话级信任状态。
     */
    public EvalResult evaluateToolCall(
            String sessionId, String assistantId, String toolName, ToolRiskLevel toolRiskLevel) {
        var defOpt = assistantRepo.findByAssistantId(assistantId);
        if (defOpt.isEmpty()) {
            return EvalResult.denied(OverLimitAction.ASK, "助理不存在或未授权", null);
        }

        var def = defOpt.get();
        var scope = def.getEffectiveScope();
        var delegatorId = def.getEffectiveDelegatorId();

        var functionChecker = functionPermissionChecker.getIfAvailable();
        var toolPermissionCode = "tool:" + normalizePermissionSegment(toolName) + ":execute";
        if (functionChecker == null
                || (!functionChecker.hasPermission(delegatorId, toolPermissionCode)
                        && !functionChecker.hasPermission(delegatorId, "tool:default:execute"))) {
            return EvalResult.denied(
                    scope.overLimitAction(),
                    "委托者没有工具执行权限 [%s]".formatted(toolPermissionCode),
                    delegatorId);
        }

        var fullDelegated = sessionTrustService.isFullDelegated(sessionId, delegatorId);
        var toolTrusted = sessionTrustService.isToolTrusted(sessionId, delegatorId, toolName);

        // 会话授权可临时扩展助理 scope，但不能绕过委托者实际权限和风险门控。
        if (!fullDelegated && !toolTrusted && !scope.isToolAllowed(toolName)) {
            return EvalResult.denied(
                    scope.overLimitAction(),
                    "工具 [%s] 不在助理允许列表内".formatted(toolName),
                    delegatorId);
        }

        // 检查风险等级
        if (!isRiskWithinLimit(toolRiskLevel, scope.maxAutoRiskLevel())) {
            return EvalResult.denied(
                    scope.overLimitAction(),
                    "工具 [%s] 风险等级 %s 超出助理自动执行上限 %s".formatted(
                            toolName, toolRiskLevel, scope.maxAutoRiskLevel()),
                    delegatorId);
        }

        return EvalResult.granted(delegatorId);
    }

    /**
     * 评估助理是否有权执行指定操作。
     *
     * @param assistantId 助理标识
     * @param operation 操作类型（read/write/delete/execute）
     * @param resource 资源标识（如 "space:my-workspace/doc-1"）
     * @return 评估结果
     */
    public EvalResult evaluateOperation(String assistantId, String operation, String resource) {
        var defOpt = assistantRepo.findByAssistantId(assistantId);
        if (defOpt.isEmpty()) {
            return EvalResult.denied(OverLimitAction.ASK, "助理不存在或未授权", null);
        }

        var def = defOpt.get();
        var scope = def.getEffectiveScope();
        var delegatorId = def.getEffectiveDelegatorId();

        if (!scope.isOperationAllowed(operation)) {
            return EvalResult.denied(
                    scope.overLimitAction(),
                    "操作 [%s] 不在助理允许列表内".formatted(operation),
                    delegatorId);
        }

        if (resource != null && !scope.isResourceAllowed(resource)) {
            return EvalResult.denied(
                    scope.overLimitAction(),
                    "资源 [%s] 不在助理允许范围内".formatted(resource),
                    delegatorId);
        }

        if (resource != null) {
            var parts = resource.split(":", 2);
            if (parts.length != 2) {
                return EvalResult.denied(scope.overLimitAction(), "资源标识格式无效", delegatorId);
            }
            var relationChecker = relationPermissionChecker.getIfAvailable();
            if (relationChecker == null
                    || !relationChecker.hasPermission(
                            delegatorId, parts[0], parts[1], "can_" + operation)) {
                return EvalResult.denied(
                        scope.overLimitAction(),
                        "委托者没有资源操作权限 [%s]".formatted(resource),
                        delegatorId);
            }
        }

        return EvalResult.granted(delegatorId);
    }

    /** 获取助理的委托者 ID */
    public Optional<Long> getDelegatorId(String assistantId) {
        return assistantRepo.findByAssistantId(assistantId)
                .map(AssistantDefinition::getEffectiveDelegatorId);
    }

    private boolean isRiskWithinLimit(ToolRiskLevel toolRisk, RiskLevel maxAuto) {
        int toolLevel = switch (toolRisk) {
            case NONE, LOW -> 1;
            case MEDIUM -> 2;
            case HIGH -> 3;
            case CRITICAL -> 4;
        };
        int maxLevel = switch (maxAuto) {
            case LOW -> 1;
            case MEDIUM -> 2;
            case HIGH -> 3;
        };
        return toolLevel <= maxLevel;
    }

    private String normalizePermissionSegment(String value) {
        return value == null ? "" : value.trim().toLowerCase().replace('_', '-');
    }
}
