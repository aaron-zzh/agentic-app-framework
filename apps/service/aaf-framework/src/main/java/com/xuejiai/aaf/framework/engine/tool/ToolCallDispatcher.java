package com.xuejiai.aaf.framework.engine.tool;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 工具调用分发器：参数校验 → 执行 → 结果封装。
 * 从 ToolRegistry 查找工具，执行并返回结构化结果。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ToolCallDispatcher {

    private final ToolRegistry registry;

    /**
     * 执行工具调用。
     *
     * @param functionName 工具名称
     * @param arguments    参数 JSON 字符串
     * @return 调用结果
     */
    public ToolCallResult dispatch(String functionName, String arguments) {
        var callback = registry.getCallback(functionName).orElse(null);
        if (callback == null) {
            return ToolCallResult.error(functionName, "工具未注册: " + functionName);
        }
        try {
            var result = callback.call(arguments);
            log.debug("工具调用成功: {} -> {}", functionName, truncate(result));
            return ToolCallResult.success(functionName, result);
        } catch (Exception e) {
            log.warn("工具调用失败: {} - {}", functionName, e.getMessage());
            return ToolCallResult.error(functionName, e.getMessage());
        }
    }

    private String truncate(String s) {
        return s != null && s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }

    /** 工具调用结果 */
    public record ToolCallResult(String functionName, boolean success, String output, String error) {
        public static ToolCallResult success(String name, String output) {
            return new ToolCallResult(name, true, output, null);
        }
        public static ToolCallResult error(String name, String error) {
            return new ToolCallResult(name, false, null, error);
        }
    }
}
