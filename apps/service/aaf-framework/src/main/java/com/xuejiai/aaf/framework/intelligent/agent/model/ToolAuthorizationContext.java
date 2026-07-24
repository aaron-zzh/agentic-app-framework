package com.xuejiai.aaf.framework.intelligent.agent.model;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ControlMode;

/** 单次 Agent 执行稳定携带的 Assistant 工具授权快照。 */
public record ToolAuthorizationContext(
        Map<String, ToolAuthorizationRule> rules, Set<String> authorizedToolKeys) {

    public ToolAuthorizationContext {
        rules = Map.copyOf(Objects.requireNonNull(rules, "rules 不能为空"));
        authorizedToolKeys =
                Set.copyOf(
                        Objects.requireNonNull(
                                authorizedToolKeys, "authorizedToolKeys 不能为空"));
        if (!authorizedToolKeys.isEmpty()) {
            throw new IllegalArgumentException("P2 不接受调用方自报工具授权");
        }
    }

    public AuthorizationDecision evaluate(
            ControlMode mode, String toolKey, boolean catalogReadOnly, boolean catalogReversible) {
        Objects.requireNonNull(mode, "controlMode 不能为空");
        var rule = rules.get(toolKey);
        if (rule == null) {
            throw new IllegalArgumentException("工具不在 Assistant 白名单: " + toolKey);
        }
        if (mode != ControlMode.READ_ONLY && mode != ControlMode.COLLABORATIVE) {
            throw new IllegalStateException("P2 尚未开放控制模式: " + mode);
        }
        if (rule.readOnly() != catalogReadOnly || rule.reversible() != catalogReversible) {
            throw new IllegalStateException("工具目录与 Assistant 策略不一致: " + toolKey);
        }
        if (mode == ControlMode.READ_ONLY && !rule.readOnly()) {
            throw new IllegalStateException("READ_ONLY 模式禁止写工具: " + toolKey);
        }
        if (mode == ControlMode.COLLABORATIVE
                && !rule.readOnly()
                && !rule.reversible()) {
            throw new IllegalStateException("COLLABORATIVE 模式只允许可撤销写工具: " + toolKey);
        }
        if (mode == ControlMode.COLLABORATIVE
                && !rule.readOnly()
                && rule.authorizationRequired()
                && !authorizedToolKeys.contains(toolKey)) {
            return AuthorizationDecision.AUTHORIZATION_REQUIRED;
        }
        return AuthorizationDecision.ALLOWED;
    }

    public record ToolAuthorizationRule(
            boolean readOnly, boolean reversible, boolean authorizationRequired) {

        public ToolAuthorizationRule {
            if (readOnly && reversible) {
                throw new IllegalArgumentException("只读工具不能标记为可撤销写入");
            }
        }
    }

    public enum AuthorizationDecision {
        ALLOWED,
        AUTHORIZATION_REQUIRED
    }
}
