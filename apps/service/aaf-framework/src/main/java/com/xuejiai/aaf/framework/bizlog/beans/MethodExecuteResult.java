package com.xuejiai.aaf.framework.bizlog.beans;

import java.lang.reflect.Method;

import lombok.Getter;

/** 方法执行结果，用于 SpEL 解析和日志组装。 */
@Getter
public class MethodExecuteResult {
    private boolean success;
    private Throwable throwable;
    private String errorMsg;
    private Object result;

    private final Method method;
    private final Object[] args;
    private final Class<?> targetClass;

    public MethodExecuteResult(Method method, Object[] args, Class<?> targetClass) {
        this.method = method;
        this.args = args;
        this.targetClass = targetClass;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public void setThrowable(Throwable throwable) {
        this.throwable = throwable;
    }

    public void setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
    }

    public void setResult(Object result) {
        this.result = result;
    }
}
