package com.xuejiai.aaf.module.system.agui;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * AG-UI 工具注册表。
 *
 * <p>管理可供 Agent 调用的工具定义，前端通过 AG-UI 协议获取工具列表。
 *
 * @author AaronZZH & Kiro
 */
@Component
public class ToolRegistry {

    /** 工具定义 */
    public record ToolDefinition(String name, String description, String parameters) {}

    private final Map<String, ToolDefinition> tools = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {
        // 预注册示例工具
        register(new ToolDefinition(
                "search_entities",
                "搜索系统中的实体记录",
                """
                {"type":"object","properties":{"query":{"type":"string","description":"搜索关键词"},"entityType":{"type":"string","description":"实体类型"}},"required":["query"]}"""));
        register(new ToolDefinition(
                "create_entity",
                "创建新的实体记录",
                """
                {"type":"object","properties":{"entityType":{"type":"string","description":"实体类型"},"data":{"type":"object","description":"实体数据"}},"required":["entityType","data"]}"""));
        register(new ToolDefinition(
                "get_weather",
                "获取指定城市的天气信息",
                """
                {"type":"object","properties":{"city":{"type":"string","description":"城市名称"}},"required":["city"]}"""));
    }

    public void register(ToolDefinition tool) {
        tools.put(tool.name(), tool);
    }

    public Optional<ToolDefinition> get(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    public Collection<ToolDefinition> getAll() {
        return tools.values();
    }
}
