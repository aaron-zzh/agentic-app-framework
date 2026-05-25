package com.xuejiai.aaf.framework.engine.tool.builtin;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import com.xuejiai.aaf.framework.engine.tool.ToolRegistry;
import com.xuejiai.aaf.framework.engine.tool.ToolRiskLevel;
import com.xuejiai.aaf.framework.engine.tool.ToolType;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

/**
 * 数据采集工具元数据注册——设置风险等级为 HIGH。
 *
 * <p>通过配置 {@code aaf.tools.data-collector.enabled=true} 启用。
 */
@Configuration
@ConditionalOnProperty(name = "aaf.tools.data-collector.enabled", havingValue = "true")
@RequiredArgsConstructor
public class DataCollectorToolConfiguration {

    private final ToolRegistry toolRegistry;

    @PostConstruct
    void registerMeta() {
        var collectMeta = new ToolRegistry.ToolMeta(
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
                },"required":["platform","taskType","query"]}""");

        var statusMeta = new ToolRegistry.ToolMeta(
                "collectStatus",
                "查询数据采集任务状态",
                ToolRegistry.SOURCE_LOCAL,
                ToolType.HTTP,
                ToolRiskLevel.LOW,
                true,
                """
                {"type":"object","properties":{
                  "taskId":{"type":"string"}
                },"required":["taskId"]}""");

        toolRegistry.registerMeta(collectMeta);
        toolRegistry.registerMeta(statusMeta);
    }
}
