package com.xuejiai.aaf.framework.intelligent.assistant.application;

import java.util.List;
import java.util.Set;

import com.xuejiai.aaf.framework.intelligent.agent.model.ToolRef;

/** 解析已激活 Skill、Role 与 Agent 共同允许的工具。 */
public interface EffectiveToolResolver {

    /**
     * 计算 Skill 工具要求 ∩ Role 工具白名单 ∩ Agent 声明工具。
     *
     * <p>任何 Skill 工具声明只能收窄当前权限，不能扩张权限。
     */
    List<ToolRef> resolve(
            Set<String> skillRequiredToolNames,
            Set<String> roleAllowedToolNames,
            List<ToolRef> agentAllowedTools);
}
