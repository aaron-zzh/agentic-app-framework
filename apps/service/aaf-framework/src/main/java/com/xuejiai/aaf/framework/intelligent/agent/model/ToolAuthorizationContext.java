package com.xuejiai.aaf.framework.intelligent.agent.model;

import java.util.Map;
import java.util.Objects;

import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ControlMode;

/** Assistant 工具可见性快照；真实授权只能由 AuthorizationGrantPort 查询。 */
public record ToolAuthorizationContext(Map<String, ToolAuthorizationRule> rules) {

    public ToolAuthorizationContext {
        rules = Map.copyOf(Objects.requireNonNull(rules, "rules 不能为空"));
    }

    public ToolAuthorizationRule requireVisible(
            ControlMode mode, String toolKey, boolean catalogReadOnly, boolean catalogReversible) {
        Objects.requireNonNull(mode, "controlMode 不能为空");
        var rule = rules.get(toolKey);
        if (rule == null) {
            throw new IllegalArgumentException("工具不在 Assistant 白名单: " + toolKey);
        }
        if (mode != ControlMode.READ_ONLY
                && mode != ControlMode.COLLABORATIVE
                && mode != ControlMode.DELEGATED) {
            throw new IllegalStateException("未开放控制模式: " + mode);
        }
        if (rule.readOnly() != catalogReadOnly || rule.reversible() != catalogReversible) {
            throw new IllegalStateException("工具目录与 Assistant 策略不一致: " + toolKey);
        }
        // 轻量态可见的授权写工具只能触发升级（promotionOnly），执行屏障在网关；该标记不得出现在任务态
        if (rule.promotionOnly() && mode != ControlMode.READ_ONLY) {
            throw new IllegalStateException("promotionOnly 工具只允许出现在轻量态: " + toolKey);
        }
        if (mode == ControlMode.READ_ONLY && !rule.readOnly() && !rule.promotionOnly()) {
            throw new IllegalStateException("READ_ONLY 模式禁止写工具: " + toolKey);
        }
        if (mode == ControlMode.COLLABORATIVE && !rule.readOnly() && !rule.reversible()) {
            throw new IllegalStateException("COLLABORATIVE 模式只允许可撤销写工具: " + toolKey);
        }
        return rule;
    }

    /**
     * @param promotionOnly 仅轻量态出现：工具对模型可见，但调用只会触发自动升级为任务，不在轻量态执行
     */
    public record ToolAuthorizationRule(
            boolean readOnly,
            boolean reversible,
            boolean authorizationRequired,
            MissingGrantBehavior missingGrantBehavior,
            boolean promotionOnly) {
        public ToolAuthorizationRule {
            if (readOnly && reversible) {
                throw new IllegalArgumentException("只读工具不能标记为可撤销写入");
            }
            Objects.requireNonNull(missingGrantBehavior, "missingGrantBehavior 不能为空");
            if (!authorizationRequired
                    && missingGrantBehavior != MissingGrantBehavior.REQUEST_ON_DEMAND) {
                throw new IllegalArgumentException("非授权工具不能指定非默认缺失授权行为");
            }
            if (promotionOnly && !authorizationRequired) {
                throw new IllegalArgumentException("promotionOnly 工具必须需要授权");
            }
        }

        public ToolAuthorizationRule(
                boolean readOnly,
                boolean reversible,
                boolean authorizationRequired,
                MissingGrantBehavior missingGrantBehavior) {
            this(readOnly, reversible, authorizationRequired, missingGrantBehavior, false);
        }
    }

    public enum MissingGrantBehavior {
        REQUEST_ON_DEMAND,
        PREAUTHORIZED_ONLY,
        DENY
    }
}
