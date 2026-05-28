package com.xuejiai.aaf.framework.intelligent.assistant;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.enums.OverLimitAction;
import com.xuejiai.aaf.common.enums.RiskLevel;
import com.xuejiai.aaf.framework.engine.tool.ToolRiskLevel;

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
        var defOpt = assistantRepo.findByAssistantId(assistantId);
        if (defOpt.isEmpty()) {
            return EvalResult.granted(null); // 未找到定义，降级为不限制
        }

        var def = defOpt.get();
        var scope = def.getEffectiveScope();
        var delegatorId = def.getEffectiveDelegatorId();

        // 检查工具是否在白名单内
        if (!scope.isToolAllowed(toolName)) {
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
            return EvalResult.granted(null);
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
}
