package com.xuejiai.aaf.framework.intelligent.action;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.RequiredArgsConstructor;

/** 业务动作工具注册——将 AiBusinessActionTool 的 @Tool 方法包装为 ToolCallback，由 ToolRegistry 收集。 */
@Configuration
@RequiredArgsConstructor
public class AiBusinessActionToolConfiguration {

    private final AiBusinessActionTool aiBusinessActionTool;

    @Bean
    public ToolCallbackProvider aiBusinessActionToolCallbackProvider() {
        return MethodToolCallbackProvider.builder().toolObjects(aiBusinessActionTool).build();
    }
}
