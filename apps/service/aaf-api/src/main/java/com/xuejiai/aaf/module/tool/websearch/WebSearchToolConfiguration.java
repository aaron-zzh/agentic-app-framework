package com.xuejiai.aaf.module.tool.websearch;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.xuejiai.aaf.framework.engine.tool.ToolRegistry;
import com.xuejiai.aaf.framework.engine.tool.ToolRiskLevel;
import com.xuejiai.aaf.framework.engine.tool.ToolType;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

/** 联网搜索工具注册——将 WebSearchTool 包装为 ToolCallback 并补充元数据；工具始终注册，未配置 api-key 时调用才报错。 */
@Configuration
@RequiredArgsConstructor
public class WebSearchToolConfiguration {

    private final WebSearchTool webSearchTool;
    private final ToolRegistry toolRegistry;

    @Bean
    public ToolCallbackProvider webSearchToolCallbackProvider() {
        return MethodToolCallbackProvider.builder().toolObjects(webSearchTool).build();
    }

    @PostConstruct
    void registerMeta() {
        toolRegistry.registerMeta(
                new ToolRegistry.ToolMeta(
                        WebSearchTool.TOOL_NAME,
                        "联网搜索互联网公开网页（阿里云 IQS）",
                        ToolRegistry.SOURCE_LOCAL,
                        ToolType.HTTP,
                        ToolRiskLevel.LOW,
                        true,
                        """
                {"type":"object","required":["query"],
                 "properties":{
                   "query":{"type":"string","description":"搜索关键词，不得包含个人隐私信息"},
                   "count":{"type":"integer","description":"返回条数，1-10，默认 5"}
                 }}"""));
    }
}
