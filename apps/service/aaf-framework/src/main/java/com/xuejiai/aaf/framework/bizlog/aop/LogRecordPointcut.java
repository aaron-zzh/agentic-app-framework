package com.xuejiai.aaf.framework.bizlog.aop;

import java.io.Serializable;
import java.lang.reflect.Method;

import org.springframework.aop.support.StaticMethodMatcherPointcut;
import org.springframework.util.CollectionUtils;

/** 匹配标注了 @LogRecord 的方法。 */
public class LogRecordPointcut extends StaticMethodMatcherPointcut implements Serializable {

    private LogRecordOperationSource logRecordOperationSource;

    @Override
    public boolean matches(Method method, Class<?> targetClass) {
        return !CollectionUtils.isEmpty(
                logRecordOperationSource.computeLogRecordOperations(method, targetClass));
    }

    void setLogRecordOperationSource(LogRecordOperationSource logRecordOperationSource) {
        this.logRecordOperationSource = logRecordOperationSource;
    }
}
