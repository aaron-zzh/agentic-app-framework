package com.xuejiai.aaf.framework.intelligent.assistant.application;

import java.util.List;
import java.util.Set;

import com.xuejiai.aaf.framework.intelligent.agent.model.ToolRef;

/** 解析已激活 Skill、Role 与 Agent 共同允许的工具。 */
public interface EffectiveToolResolver {

    /**
     * 计算 Skill 工具要求 ∩ Role 工具白名单 ∩ Agent 声明工具，并入 {@link BaseToolProfile}。
     *
     * <p>{@code inheritRoleTools=false}（RESTRICT）时，Skill 工具声明只能收窄当前权限，不能扩张权限。 {@code
     * inheritRoleTools=true}（INHERIT，仅限已审核系统 Skill）时，跳过 {@code skillRequiredToolNames} 限制，直接放行 Role
     * 与 Agent 交集的全部业务工具。
     */
    List<ToolRef> resolve(
            Set<String> skillRequiredToolNames,
            boolean inheritRoleTools,
            Set<String> roleAllowedToolNames,
            List<ToolRef> agentAllowedTools);

    /**
     * Assistant Skill 只能获得其必需工具与 Assistant、Agent 白名单的严格交集，并入 {@link BaseToolProfile}；{@code
     * inheritRoleTools} 语义与 {@link #resolve} 一致。
     */
    List<ToolRef> resolveAssistant(
            Set<String> skillRequiredToolNames,
            boolean inheritRoleTools,
            Set<String> assistantAllowedToolNames,
            List<ToolRef> agentAllowedTools);
}
