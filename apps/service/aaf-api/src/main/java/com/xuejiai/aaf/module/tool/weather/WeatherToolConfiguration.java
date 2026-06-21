package com.xuejiai.aaf.module.tool.weather;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.xuejiai.aaf.framework.engine.tool.ToolRegistry;
import com.xuejiai.aaf.framework.engine.tool.ToolRiskLevel;
import com.xuejiai.aaf.framework.engine.tool.ToolType;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

/** 天气查询工具注册——将 WeatherTool 包装为 ToolCallback 并补充元数据。 */
@Configuration
@RequiredArgsConstructor
public class WeatherToolConfiguration {

    private final WeatherTool weatherTool;
    private final ToolRegistry toolRegistry;

    @Bean
    public ToolCallbackProvider weatherToolCallbackProvider() {
        return MethodToolCallbackProvider.builder().toolObjects(weatherTool).build();
    }

    @PostConstruct
    void registerMeta() {
        toolRegistry.registerMeta(
                new ToolRegistry.ToolMeta(
                        "queryWeather",
                        "查询实时天气（彩云天气）",
                        ToolRegistry.SOURCE_LOCAL,
                        ToolType.HTTP,
                        ToolRiskLevel.LOW,
                        true,
                        """
                {"type":"object","required":["longitude","latitude"],
                 "properties":{
                   "longitude":{"type":"number","description":"经度，如 116.3883"},
                   "latitude":{"type":"number","description":"纬度，如 39.9289"}
                 }}"""));
    }
}
