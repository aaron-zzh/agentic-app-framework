package com.xuejiai.aaf.framework.engine.tool;

import java.util.List;
import java.util.Objects;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.intelligent.assistant.role.RoleStore;

import lombok.RequiredArgsConstructor;

/**
 * 工具解析器——按 roleId / agentId 从 ToolRegistry 过滤出可用工具。
 *
 * <p>两级工具收窄模型：
 *
 * <ul>
 *   <li>角色级（Role.toolWhitelist）：授权池，定义角色允许使用的工具上限
 *   <li>Agent 级（AgentDefinition.allowedTools）：执行白名单，Agent 实际可调用的工具
 * </ul>
 *
 * <p>运行时逐层取交集（角色池 ∩ Agent 白名单）最终由 Agent 调用。技能不再绑定工具。
 *
 * <p>两条链路共用同一入口：
 *
 * <ul>
 *   <li>Spring AI 链路：直接使用返回的 {@link ToolCallback} 列表
 *   <li>AgentScope 链路：由 {@code AgentScopeToolkitFactory} 把 ToolCallback 适配为 AgentTool
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class ToolResolver {

    private final ToolRegistry toolRegistry;
    private final RoleStore roleStore;

    /**
     * 按 roleId 解析工具（走 RoleStore 授权池）。
     *
     * @param roleId 角色 ID，为 null 时返回全部工具
     */
    public List<ToolCallback> resolveForRole(Long roleId) {
        return toolRegistry.resolveForRole(roleId);
    }

    /**
     * 两级工具收窄：角色级授权池（tool_whitelist） ∩ Agent 级执行白名单（allowed_tools）。
     *
     * <p>解析规则：
     *
     * <ul>
     *   <li>角色授权池为空 → 不限制，取 Agent 白名单
     *   <li>Agent 白名单为空 → 不限制，取角色授权池
     *   <li>两者均非空 → 取交集
     *   <li>两者均为空 → 返回全部已注册工具
     * </ul>
     *
     * @param roleId 角色 ID（角色级授权池来源），可为 null
     * @param agentAllowedTools Agent 级执行白名单（来自 AgentDefinition.allowedTools），可为空
     */
    public List<ToolCallback> resolveForRoleAndAgent(Long roleId, List<String> agentAllowedTools) {
        var rolePool = roleId == null ? List.<String>of() : roleStore.getToolWhitelist(roleId);
        var agentList = agentAllowedTools == null ? List.<String>of() : agentAllowedTools;

        List<String> effective;
        if (rolePool.isEmpty()) {
            effective = agentList;
        } else if (agentList.isEmpty()) {
            effective = rolePool;
        } else {
            // 两级均限定 → 取交集
            effective = rolePool.stream().filter(agentList::contains).toList();
        }
        return resolveByNames(effective);
    }

    /**
     * 按 agentId 解析工具（走 AgentDefinition.allowedTools 执行白名单）。
     *
     * @param agentId Agent ID
     * @param toolNames Agent 允许的工具名列表（来自 AgentDefinition.allowedTools/tools），为空则返回全部
     */
    public List<ToolCallback> resolveForAgent(Long agentId, List<String> toolNames) {
        if (toolNames == null || toolNames.isEmpty()) {
            return toolRegistry.resolveForRole(null);
        }
        return resolveByNames(toolNames);
    }

    /**
     * 按工具名列表直接解析（通用）。
     *
     * @param toolNames 工具名列表，为空则返回全部
     */
    public List<ToolCallback> resolveByNames(List<String> toolNames) {
        if (toolNames == null || toolNames.isEmpty()) {
            return toolRegistry.listAll().stream()
                    .map(meta -> toolRegistry.getCallback(meta.name()).orElse(null))
                    .filter(Objects::nonNull)
                    .toList();
        }
        return toolNames.stream()
                .map(name -> toolRegistry.getCallback(name).orElse(null))
                .filter(Objects::nonNull)
                .toList();
    }
}
