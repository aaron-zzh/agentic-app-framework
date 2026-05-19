package com.xuejiai.aaf.framework.intelligent.core.function;

import java.util.List;
import java.util.Map;

/**
 * 工具函数定义（对齐 OpenAI Function Calling Schema）。
 * Core 层接口契约，Agent 层负责注册与调用实现。
 */
public record FunctionDefinition(
    String name,
    String description,
    Map<String, Object> parameters  // JSON Schema 格式的参数定义
) {

    /**
     * 工具提供者接口——Agent 层实现，向工具系统注册可用工具。
     * Core 层只定义契约，不依赖具体实现。
     */
    public interface ToolProvider {

        /** 返回该提供者暴露的所有工具定义 */
        List<FunctionDefinition> getDefinitions();

        /**
         * 执行工具调用。
         *
         * @param name      工具名称
         * @param arguments 参数 JSON 字符串
         * @return 执行结果字符串
         */
        String call(String name, String arguments);
    }
}
