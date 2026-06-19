package com.xuejiai.aaf.framework.engine.tool.builtin;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.xuejiai.aaf.framework.engine.tool.ToolRegistry;
import com.xuejiai.aaf.framework.engine.tool.ToolRiskLevel;
import com.xuejiai.aaf.framework.engine.tool.ToolType;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

/**
 * 数据采集工具注册——将 DataCollectorTool 包装为 ToolCallback 并补充元数据。
 *
 * <p>通过配置 {@code aaf.tools.data-collector.enabled=true} 启用。
 */
@Configuration
@ConditionalOnProperty(name = "aaf.tools.data-collector.enabled", havingValue = "true")
@RequiredArgsConstructor
public class DataCollectorToolConfiguration {

    private final DataCollectorTool dataCollectorTool;
    private final ToolRegistry toolRegistry;

    @Bean
    public ToolCallbackProvider dataCollectorToolCallbackProvider() {
        return MethodToolCallbackProvider.builder().toolObjects(dataCollectorTool).build();
    }

    @PostConstruct
    void registerMeta() {
        toolRegistry.registerMeta(
                new ToolRegistry.ToolMeta(
                        "collect",
                        "从外部数据源采集社交媒体数据",
                        ToolRegistry.SOURCE_LOCAL,
                        ToolType.HTTP,
                        ToolRiskLevel.HIGH,
                        false,
                        """
                {"type":"object","properties":{
                  "platform":{"type":"string","enum":["douyin","xiaohongshu","bilibili","weibo"]},
                  "taskType":{"type":"string","enum":["search","user_posts","comments","video_detail"]},
                  "query":{"type":"string"},
                  "limit":{"type":"integer","default":20}
                },"required":["platform","taskType","query"]}"""));

        toolRegistry.registerMeta(
                new ToolRegistry.ToolMeta(
                        "collectStatus",
                        "查询数据采集任务状态",
                        ToolRegistry.SOURCE_LOCAL,
                        ToolType.HTTP,
                        ToolRiskLevel.LOW,
                        true,
                        """
                {"type":"object","properties":{
                  "taskId":{"type":"string"}
                },"required":["taskId"]}"""));
    }
}
