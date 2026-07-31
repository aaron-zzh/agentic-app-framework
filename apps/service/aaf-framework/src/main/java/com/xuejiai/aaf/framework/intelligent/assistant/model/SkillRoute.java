package com.xuejiai.aaf.framework.intelligent.assistant.model;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import com.xuejiai.aaf.framework.intelligent.agent.model.SubagentSpec;

/** 从用户意图路由到子智能体规格的稳定规则。 */
public record SkillRoute(
        String skillKey,
        String roleKey,
        Set<String> intentTerms,
        SubagentSpec subagentSpec,
        String actionKey,
        ToolPolicy.ActionEffect actionEffect,
        HandlingMode handlingMode,
        int priority,
        boolean defaultRoute) {

    public SkillRoute {
        skillKey = requireText(skillKey, "skillKey");
        roleKey = requireText(roleKey, "roleKey");
        intentTerms = Set.copyOf(Objects.requireNonNull(intentTerms, "intentTerms 不能为空"));
        intentTerms.forEach(term -> requireText(term, "intentTerm"));
        Objects.requireNonNull(subagentSpec, "subagentSpec 不能为空");
        actionKey = requireText(actionKey, "actionKey");
        Objects.requireNonNull(actionEffect, "actionEffect 不能为空");
        Objects.requireNonNull(handlingMode, "handlingMode 不能为空");
        if (!defaultRoute && intentTerms.isEmpty()) {
            throw new IllegalArgumentException("非默认 SkillRoute 必须声明 intentTerms");
        }
    }

    /** 仅作为模型不可用时的确定性兜底，不承担主要语义前注意。 */
    public boolean matches(String input) {
        if (input == null || input.isBlank() || intentTerms.isEmpty()) {
            return false;
        }
        var normalized = input.toLowerCase(Locale.ROOT);
        return intentTerms.stream()
                .map(term -> term.toLowerCase(Locale.ROOT))
                .anyMatch(normalized::contains);
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name + " 不能为空");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " 不能为空白");
        }
        return value;
    }

    public enum HandlingMode {
        DIRECT,
        DELEGATE
    }
}
