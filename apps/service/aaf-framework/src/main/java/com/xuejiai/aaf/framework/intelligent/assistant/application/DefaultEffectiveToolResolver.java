package com.xuejiai.aaf.framework.intelligent.assistant.application;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.xuejiai.aaf.framework.intelligent.agent.model.ToolRef;

/** 工具编译期最小权限交集解析器。 */
public final class DefaultEffectiveToolResolver implements EffectiveToolResolver {

    @Override
    public List<ToolRef> resolve(
            Set<String> skillRequiredToolNames,
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
        var result =
                agentWhitelist.stream()
                        .filter(
                                tool ->
                                        roleWhitelist.isEmpty()
                                                || roleWhitelist.contains(tool.name()))
                        .filter(
                                tool ->
                                        skillRequirements.isEmpty()
                                                || skillRequirements.contains(tool.name()))
                        .toList();
        if (!skillRequirements.isEmpty()
                && !result.stream()
                        .map(ToolRef::name)
                        .collect(java.util.stream.Collectors.toSet())
                        .containsAll(skillRequirements)) {
            throw new IllegalStateException("已激活 Skill 的必需工具不在 Role 与 Agent 交集内");
        }
        return result;
    }
}
