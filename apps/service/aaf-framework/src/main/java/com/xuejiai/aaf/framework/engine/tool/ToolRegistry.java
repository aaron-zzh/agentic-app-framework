package com.xuejiai.aaf.framework.engine.tool;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.intelligent.core.function.FunctionDefinition;

import lombok.extern.slf4j.Slf4j;

/**
 * 工具注册表：Spring Bean 自动发现 + MCP 工具 + 手动注册。
 * 实现 ToolProvider 接口，按 assistantId 白名单过滤。
 */
@Slf4j
@Component
public class ToolRegistry implements FunctionDefinition.ToolProvider {

    private final Map<String, ToolCallback> callbacks = new ConcurrentHashMap<>();
    private final Map<String, FunctionDefinition> definitions = new ConcurrentHashMap<>();
    /** assistantId → 允许的工具名列表，null 表示全部允许 */
    private final Map<String, List<String>> whitelist = new ConcurrentHashMap<>();

    /** Spring 自动注入所有 ToolCallback Bean */
    @Autowired(required = false)
    public void setToolCallbacks(List<ToolCallback> toolCallbacks) {
        if (toolCallbacks != null) {
            toolCallbacks.forEach(this::register);
        }
    }

    /** 注册 Spring AI ToolCallback */
    public void register(ToolCallback callback) {
        var name = callback.getToolDefinition().name();
        callbacks.put(name, callback);
        definitions.put(name, new FunctionDefinition(
            name,
            callback.getToolDefinition().description(),
            Map.of("type", "object", "properties", Map.of())
        ));
        log.info("注册工具: {}", name);
    }

    /** 手动注册自定义工具 */
    public void register(FunctionDefinition definition, ToolCallback callback) {
        definitions.put(definition.name(), definition);
        callbacks.put(definition.name(), callback);
        log.info("注册自定义工具: {}", definition.name());
    }

    /** 设置 Assistant 工具白名单 */
    public void setWhitelist(String assistantId, List<String> toolNames) {
        whitelist.put(assistantId, toolNames);
    }

    /** 获取 Assistant 可用工具（按白名单过滤） */
    public List<ToolCallback> resolveForAssistant(String assistantId) {
        var allowed = whitelist.get(assistantId);
        if (allowed == null) return List.copyOf(callbacks.values());
        return allowed.stream()
            .map(callbacks::get)
            .filter(Objects::nonNull)
            .toList();
    }

    @Override
    public List<FunctionDefinition> getDefinitions() {
        return List.copyOf(definitions.values());
    }

    @Override
    public String call(String name, String arguments) {
        var cb = callbacks.get(name);
        if (cb == null) throw new IllegalArgumentException("工具未注册: " + name);
        return cb.call(arguments);
    }

    public Optional<ToolCallback> getCallback(String name) {
        return Optional.ofNullable(callbacks.get(name));
    }

    public Set<String> listNames() {
        return Set.copyOf(callbacks.keySet());
    }
}
