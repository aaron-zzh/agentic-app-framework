package com.xuejiai.aaf.framework.engine.tool;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.intelligent.assistant.role.RoleStore;
import com.xuejiai.aaf.framework.intelligent.core.function.FunctionDefinition;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 工具注册中心——统一管理所有来源的工具。
 *
 * <p>注册来源：
 *
 * <ul>
 *   <li>LOCAL — Spring Bean 自动发现（@Tool 注解的 ToolCallback）
 *   <li>MCP — MCP Server 动态注册
 *   <li>CUSTOM — 用户自定义（通过 API 注册）
 * </ul>
 *
 * <p>白名单从 Role 获取（与技能关联统一）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolRegistry implements FunctionDefinition.ToolProvider {

    private final RoleStore roleStore;

    /** 工具回调 */
    private final Map<String, ToolCallback> callbacks = new ConcurrentHashMap<>();

    /** 工具定义（元数据） */
    private final Map<String, ToolMeta> metas = new ConcurrentHashMap<>();

    /** 工具元数据（含来源、类型、风险等级） */
    public record ToolMeta(
            String name,
            String description,
            String source,
            ToolType type,
            ToolRiskLevel riskLevel,
            boolean readOnly,
            String parametersSchema) {
        /** 兼容旧构造 */
        public ToolMeta(String name, String description, String source, String parametersSchema) {
            this(
                    name,
                    description,
                    source,
                    ToolType.FUNCTION,
                    ToolRiskLevel.NONE,
                    false,
                    parametersSchema);
        }
    }

    public static final String SOURCE_LOCAL = "LOCAL";
    public static final String SOURCE_MCP = "MCP";
    public static final String SOURCE_CUSTOM = "CUSTOM";

    /** Spring 自动注入所有 ToolCallback Bean */
    @Autowired(required = false)
    public void setToolCallbacks(List<ToolCallback> toolCallbacks) {
        if (toolCallbacks != null) {
            toolCallbacks.forEach(cb -> register(cb, SOURCE_LOCAL));
        }
    }

    /** 注册工具（带来源标记）。 */
    public void register(ToolCallback callback, String source) {
        var name = callback.getToolDefinition().name();
        var desc = callback.getToolDefinition().description();
        callbacks.put(name, callback);
        metas.put(
                name,
                new ToolMeta(
                        name, desc, source, ToolType.FUNCTION, ToolRiskLevel.NONE, false, null));
        log.info("注册工具: {} [{}]", name, source);
    }

    /** 注册自定义工具。 */
    public void register(FunctionDefinition definition, ToolCallback callback, String source) {
        callbacks.put(definition.name(), callback);
        metas.put(
                definition.name(),
                new ToolMeta(
                        definition.name(),
                        definition.description(),
                        source,
                        ToolType.FUNCTION,
                        ToolRiskLevel.NONE,
                        false,
                        null));
        log.info("注册自定义工具: {} [{}]", definition.name(), source);
    }

    /** 按 Role 白名单获取可用工具。 */
    public List<ToolCallback> resolveForRole(String roleId) {
        if (roleId == null || roleId.isBlank()) {
            return List.of();
        }
        var allowed = roleStore.getToolWhitelist(roleId);
        if (allowed.isEmpty()) return List.copyOf(callbacks.values());
        return allowed.stream().map(callbacks::get).filter(Objects::nonNull).toList();
    }

    /** 获取所有工具元数据。 */
    public List<ToolMeta> listAll() {
        return List.copyOf(metas.values());
    }

    /** 按来源过滤。 */
    public List<ToolMeta> listBySource(String source) {
        return metas.values().stream().filter(m -> source.equals(m.source())).toList();
    }

    /** 调用工具。 */
    @Override
    public String call(String name, String arguments) {
        var cb = callbacks.get(name);
        if (cb == null) throw new IllegalArgumentException("工具未注册: " + name);
        return cb.call(arguments);
    }

    @Override
    public List<FunctionDefinition> getDefinitions() {
        return metas.values().stream()
                .map(
                        m ->
                                new FunctionDefinition(
                                        m.name(),
                                        m.description(),
                                        Map.of("type", "object", "properties", Map.of())))
                .toList();
    }

    public Optional<ToolCallback> getCallback(String name) {
        return Optional.ofNullable(callbacks.get(name));
    }

    public Set<String> listNames() {
        return Set.copyOf(callbacks.keySet());
    }

    // 兼容旧接口
    public void register(ToolCallback callback) {
        register(callback, SOURCE_LOCAL);
    }

    public void register(FunctionDefinition def, ToolCallback cb) {
        register(def, cb, SOURCE_CUSTOM);
    }

    public void registerMeta(ToolMeta meta) {
        metas.put(meta.name(), meta);
    }

    public void setWhitelist(String assistantId, List<String> toolNames) {
        /* 已废弃，走 Role */
    }

    public List<ToolCallback> resolveForAssistant(String assistantId) {
        return List.copyOf(callbacks.values());
    }
}
