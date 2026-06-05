package com.xuejiai.aaf.framework.engine.tool;

import java.util.List;
import java.util.Objects;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.engine.skill.SkillStore;
import com.xuejiai.aaf.framework.intelligent.assistant.role.RoleStore;

import lombok.RequiredArgsConstructor;

/**
 * 工具解析器——按 roleId / skillId / agentId 从 ToolRegistry 过滤出可用工具。
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
    private final SkillStore skillStore;

    /**
     * 按 roleId 解析工具（走 RoleStore 白名单）。
     *
     * @param roleId 角色 ID，为 null 时返回全部工具
     */
    public List<ToolCallback> resolveForRole(String roleId) {
        return toolRegistry.resolveForRole(roleId);
    }

    /**
     * 按 skillId 解析工具（从 SkillRecord.tools 白名单取）。
     *
     * @param skillId 技能 ID，未找到或无工具配置时返回空列表
     */
    public List<ToolCallback> resolveForSkill(String skillId) {
        return skillStore
                .findBySkillId(skillId)
                .map(skill -> resolveByNames(parseList(skill.tools())))
                .orElse(List.of());
    }

    /**
     * 按 agentId 解析工具（走 AgentDefinition.tools 白名单字段）。
     *
     * @param agentId Agent ID
     * @param toolNames Agent 允许的工具名列表（来自 AgentDefinition.tools），为空则返回全部
     */
    public List<ToolCallback> resolveForAgent(String agentId, List<String> toolNames) {
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

    private List<String> parseList(String json) {
        if (json == null || json.isBlank()) return List.of();
        return List.of(json.replaceAll("[\\[\\]\"\\ ]", "").split(",")).stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
