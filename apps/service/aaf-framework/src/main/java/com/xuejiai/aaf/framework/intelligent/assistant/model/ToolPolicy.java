package com.xuejiai.aaf.framework.intelligent.assistant.model;

import java.util.Map;
import java.util.Objects;

import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ControlMode;

/** Assistant 对业务动作和工具副作用的只读策略。 */
public record ToolPolicy(Map<String, ToolRule> rules) {

    public ToolPolicy {
        rules = Map.copyOf(Objects.requireNonNull(rules, "ToolPolicy rules 不能为空"));
        rules.forEach(
                (key, rule) -> {
                    if (key == null || key.isBlank()) {
                        throw new IllegalArgumentException("工具 key 不能为空白");
                    }
                    Objects.requireNonNull(rule, "ToolRule 不能为空");
                    if (!key.equals(rule.toolKey())) {
                        throw new IllegalArgumentException(
                                "ToolPolicy key 必须与 ToolRule.toolKey 一致");
                    }
                });
    }

    /** P2 只允许 READ_ONLY 与 COLLABORATIVE，且协作写入必须可撤销。 */
    public boolean allows(ControlMode mode, ActionEffect effect) {
        Objects.requireNonNull(mode, "controlMode 不能为空");
        Objects.requireNonNull(effect, "actionEffect 不能为空");
        return switch (mode) {
            case READ_ONLY ->
                    effect == ActionEffect.READ
                            || effect == ActionEffect.GENERATED_CONTENT
                            || effect == ActionEffect.HUMAN_HANDOFF;
            case COLLABORATIVE -> effect != ActionEffect.IRREVERSIBLE_WRITE;
            case DELEGATED -> true;
            case AUTOMATED -> false;
        };
    }

    public void requireAllowed(ControlMode mode, String toolKey) {
        var rule = rules.get(toolKey);
        if (rule == null) {
            throw new IllegalArgumentException("工具不在 Assistant 白名单: " + toolKey);
        }
        if (!allows(mode, rule.effect())) {
            throw new IllegalStateException("当前控制模式禁止工具动作: " + toolKey);
        }
        if (mode == ControlMode.COLLABORATIVE
                && rule.effect() == ActionEffect.REVERSIBLE_WRITE
                && !rule.reversible()) {
            throw new IllegalStateException("COLLABORATIVE 写操作必须可撤销: " + toolKey);
        }
    }

    public record ToolRule(
            String toolKey,
            ActionEffect effect,
            boolean reversible,
            boolean authorizationRequired) {

        public ToolRule {
            if (toolKey == null || toolKey.isBlank()) {
                throw new IllegalArgumentException("toolKey 不能为空白");
            }
            Objects.requireNonNull(effect, "effect 不能为空");
            if (effect == ActionEffect.IRREVERSIBLE_WRITE && reversible) {
                throw new IllegalArgumentException("不可逆写操作不能标记为可撤销");
            }
        }
    }

    public enum ActionEffect {
        READ,
        GENERATED_CONTENT,
        REVERSIBLE_WRITE,
        IRREVERSIBLE_WRITE,
        HUMAN_HANDOFF
    }
}
