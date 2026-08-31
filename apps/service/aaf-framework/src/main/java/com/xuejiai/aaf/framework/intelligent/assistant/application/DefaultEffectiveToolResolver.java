package com.xuejiai.aaf.framework.intelligent.assistant.application;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.xuejiai.aaf.framework.intelligent.agent.model.ToolRef;

/** 工具编译期最小权限交集解析器。 */
public final class DefaultEffectiveToolResolver implements EffectiveToolResolver {

    @Override
    public List<ToolRef> resolve(
            Set<String> skillRequiredToolNames,
            boolean inheritRoleTools,
            Set<String> roleAllowedToolNames,
            List<ToolRef> agentAllowedTools) {
        var skillRequirements =
                Set.copyOf(
                        Objects.requireNonNull(
                                skillRequiredToolNames, "skillRequiredToolNames 不能为空"));
        var roleWhitelist =
                Set.copyOf(
                        Objects.requireNonNull(roleAllowedToolNames, "roleAllowedToolNames 不能为空"));
        var agentWhitelist =
                List.copyOf(Objects.requireNonNull(agentAllowedTools, "agentAllowedTools 不能为空"));
        if (agentWhitelist.isEmpty() && !skillRequirements.isEmpty()) {
            throw new IllegalStateException("Agent 未声明工具，无法满足已激活 Skill 的必需工具");
        }
        var baseTools =
                agentWhitelist.stream()
                        .filter(tool -> BaseToolProfile.toolNames().contains(tool.name()))
                        .collect(Collectors.toUnmodifiableSet());
        // INHERIT：跳过 skillRequiredToolNames 限制，放行 Role 与 Agent 交集的全部业务工具，
        // 只允许已审核系统 Skill 声明（由发布门禁 requireInheritOnlyForBuiltIn 保证）。
        if (inheritRoleTools) {
            var inherited =
                    agentWhitelist.stream()
                            .filter(
                                    tool ->
                                            roleWhitelist.isEmpty()
                                                    || roleWhitelist.contains(tool.name()))
                            .collect(Collectors.toUnmodifiableSet());
            return mergeDistinct(baseTools, inherited);
        }
        // RESTRICT + 空集合：Skill 未声明任何工具需求时不放开业务工具，仅保留 BaseToolProfile，
        // 避免与 resolveAssistant() 空集合语义相反（同一输入两种解释）。
        if (skillRequirements.isEmpty()) {
            return List.copyOf(baseTools);
        }
        var restricted =
                agentWhitelist.stream()
                        .filter(
                                tool ->
                                        roleWhitelist.isEmpty()
                                                || roleWhitelist.contains(tool.name()))
                        .filter(tool -> skillRequirements.contains(tool.name()))
                        .toList();
        if (!restricted.stream()
                .map(ToolRef::name)
                .collect(Collectors.toSet())
                .containsAll(skillRequirements)) {
            throw new IllegalStateException("已激活 Skill 的必需工具不在 Role 与 Agent 交集内");
        }
        return mergeDistinct(baseTools, restricted);
    }

    @Override
    public List<ToolRef> resolveAssistant(
            Set<String> skillRequiredToolNames,
            boolean inheritRoleTools,
            Set<String> assistantAllowedToolNames,
            List<ToolRef> agentAllowedTools) {
        var skillRequirements =
                Set.copyOf(
                        Objects.requireNonNull(
                                skillRequiredToolNames, "skillRequiredToolNames 不能为空"));
        var assistantWhitelist =
                Set.copyOf(
                        Objects.requireNonNull(
                                assistantAllowedToolNames, "assistantAllowedToolNames 不能为空"));
        var agentWhitelist =
                List.copyOf(Objects.requireNonNull(agentAllowedTools, "agentAllowedTools 不能为空"));
        var baseTools =
                agentWhitelist.stream()
                        .filter(tool -> BaseToolProfile.toolNames().contains(tool.name()))
                        .collect(Collectors.toUnmodifiableSet());
        if (inheritRoleTools) {
            var inherited =
                    agentWhitelist.stream()
                            .filter(tool -> assistantWhitelist.contains(tool.name()))
                            .collect(Collectors.toUnmodifiableSet());
            return mergeDistinct(baseTools, inherited);
        }
        if (skillRequirements.isEmpty()) {
            return List.copyOf(baseTools);
        }
        var restricted =
                agentWhitelist.stream()
                        .filter(tool -> assistantWhitelist.contains(tool.name()))
                        .filter(tool -> skillRequirements.contains(tool.name()))
                        .toList();
        var resolved = restricted.stream().map(ToolRef::name).collect(Collectors.toSet());
        if (!resolved.containsAll(skillRequirements)) {
            throw new IllegalStateException("Assistant Skill 的必需工具不在 Assistant 与 Agent 交集内");
        }
        return mergeDistinct(baseTools, restricted);
    }

    private static List<ToolRef> mergeDistinct(
            Set<ToolRef> baseTools, Collection<ToolRef> additional) {
        var merged = new java.util.LinkedHashSet<ToolRef>(baseTools);
        merged.addAll(additional);
        return List.copyOf(merged);
    }
}
