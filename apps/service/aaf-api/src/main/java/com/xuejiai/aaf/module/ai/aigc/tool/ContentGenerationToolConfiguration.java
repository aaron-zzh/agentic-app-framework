package com.xuejiai.aaf.module.ai.aigc.tool;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.RequiredArgsConstructor;

/** 生成式内容工具注册——将 ContentGenerationTool 的 @Tool 方法包装为 ToolCallback，由 ToolRegistry 收集。 */
@Configuration
@RequiredArgsConstructor
public class ContentGenerationToolConfiguration {

    private final ContentGenerationTool contentGenerationTool;

    @Bean
    public ToolCallbackProvider contentGenerationToolCallbackProvider() {
        return MethodToolCallbackProvider.builder().toolObjects(contentGenerationTool).build();
    }
}
