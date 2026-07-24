package com.xuejiai.aaf.framework.intelligent.assistant.model;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.AgentId;

/** 从用户意图路由到精确 Agent 定义版本的稳定规则。 */
public record SkillRoute(
        String skillKey,
        String name,
        Set<String> intentTerms,
        AgentId agentId,
        long agentDefinitionVersion,
        String actionKey,
        ToolPolicy.ActionEffect actionEffect,
        int priority,
        boolean defaultRoute) {

    public SkillRoute {
        skillKey = requireText(skillKey, "skillKey");
        name = requireText(name, "name");
        intentTerms = Set.copyOf(Objects.requireNonNull(intentTerms, "intentTerms 不能为空"));
        intentTerms.forEach(term -> requireText(term, "intentTerm"));
        Objects.requireNonNull(agentId, "agentId 不能为空");
        actionKey = requireText(actionKey, "actionKey");
        Objects.requireNonNull(actionEffect, "actionEffect 不能为空");
        if (agentDefinitionVersion < 1) {
            throw new IllegalArgumentException("agentDefinitionVersion 必须大于 0");
        }
        if (!defaultRoute && intentTerms.isEmpty()) {
            throw new IllegalArgumentException("非默认 SkillRoute 必须声明 intentTerms");
        }
    }

    /** 仅做确定性前注意匹配，不调用模型。 */
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
}
