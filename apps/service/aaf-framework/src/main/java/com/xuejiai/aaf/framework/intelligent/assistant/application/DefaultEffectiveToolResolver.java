package com.xuejiai.aaf.framework.intelligent.assistant.application;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.xuejiai.aaf.framework.intelligent.agent.model.ToolRef;

/** 工具双层白名单的默认解析实现。 */
public final class DefaultEffectiveToolResolver implements EffectiveToolResolver {

    @Override
    public List<ToolRef> resolve(
            Set<String> roleAllowedToolNames, List<ToolRef> agentAllowedTools) {
        var roleWhitelist =
                Set.copyOf(
                        Objects.requireNonNull(
                                roleAllowedToolNames, "roleAllowedToolNames 不能为空"));
        var agentWhitelist =
                List.copyOf(
                        Objects.requireNonNull(agentAllowedTools, "agentAllowedTools 不能为空"));
        if (roleWhitelist.isEmpty()) {
            return agentWhitelist;
        }
        if (agentWhitelist.isEmpty()) {
            throw new IllegalStateException("Agent 层未声明 allowedTools，无法与角色白名单取交集");
        }
        return agentWhitelist.stream()
                .filter(tool -> roleWhitelist.contains(tool.name()))
                .toList();
    }
}
