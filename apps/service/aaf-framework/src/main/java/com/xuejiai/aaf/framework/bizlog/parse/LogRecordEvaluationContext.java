package com.xuejiai.aaf.framework.bizlog.parse;

import java.lang.reflect.Method;
import java.util.Map;

import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.ParameterNameDiscoverer;

import com.xuejiai.aaf.framework.bizlog.context.LogRecordContext;

/**
 * 操作日志 SpEL 求值上下文，在 MethodBasedEvaluationContext 基础上： 1. 注入 LogRecordContext 中的方法级变量和全局变量 2. 注入
 * _ret（方法返回值）和 _errorMsg（异常信息）两个内置变量
 */
public class LogRecordEvaluationContext extends MethodBasedEvaluationContext {

    public LogRecordEvaluationContext(
            Object rootObject,
            Method method,
            Object[] arguments,
            ParameterNameDiscoverer parameterNameDiscoverer,
            Object ret,
            String errorMsg) {
        super(rootObject, method, arguments, parameterNameDiscoverer);
        // 注入方法级变量（优先级高）
        Map<String, Object> variables = LogRecordContext.getVariables();
        if (variables != null) setVariables(variables);
        // 注入全局变量（优先级低，已有同名变量时跳过）
        Map<String, Object> globalVariable = LogRecordContext.getGlobalVariableMap();
        if (globalVariable != null) {
            globalVariable.forEach(
                    (key, value) -> {
                        if (lookupVariable(key) == null) setVariable(key, value);
                    });
        }
        setVariable("_ret", ret);
        setVariable("_errorMsg", errorMsg);
    }
}
