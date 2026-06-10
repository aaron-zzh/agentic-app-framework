package com.xuejiai.aaf.framework.bizlog.parse;

import java.util.Map;

import org.springframework.util.StringUtils;

import com.xuejiai.aaf.framework.bizlog.service.IFunctionService;

import lombok.AllArgsConstructor;

/** 自定义函数解析器，从 IFunctionService 调用已注册的 IParseFunction。 */
@AllArgsConstructor
public class LogFunctionParser {

    private IFunctionService functionService;

    /**
     * 获取函数执行结果。 若 functionName 为空直接返回值的字符串表示； 若在 beforeFunctionNameAndReturnMap
     * 中已有缓存（executeBefore=true 预执行）则直接取缓存。
     */
    public String getFunctionReturnValue(
            Map<String, String> beforeFunctionNameAndReturnMap,
            Object value,
            String expression,
            String functionName) {
        if (!StringUtils.hasText(functionName)) {
            return value == null ? "" : value.toString();
        }
        String callKey = getFunctionCallInstanceKey(functionName, expression);
        if (beforeFunctionNameAndReturnMap != null
                && beforeFunctionNameAndReturnMap.containsKey(callKey)) {
            return beforeFunctionNameAndReturnMap.get(callKey);
        }
        return functionService.apply(functionName, value);
    }

    /** 函数缓存键：函数名 + 参数表达式，保证同方法内相同调用只执行一次。 */
    public String getFunctionCallInstanceKey(String functionName, String paramExpression) {
        return functionName + paramExpression;
    }

    public boolean beforeFunction(String functionName) {
        return functionService.beforeFunction(functionName);
    }

    public void setFunctionService(IFunctionService functionService) {
        this.functionService = functionService;
    }
}
