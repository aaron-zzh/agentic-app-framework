package com.xuejiai.aaf.framework.bizlog.aop;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ClassUtils;
import org.springframework.util.ConcurrentReferenceHashMap;
import org.springframework.util.StringUtils;

import com.xuejiai.aaf.framework.bizlog.annotation.LogRecord;
import com.xuejiai.aaf.framework.bizlog.annotation.LogRecords;
import com.xuejiai.aaf.framework.bizlog.beans.LogRecordOps;

/** 解析方法上的 @LogRecord 注解，转换为 LogRecordOps 列表。 */
public class LogRecordOperationSource {

    private static final Map<Method, Method> INTERFACE_METHOD_CACHE =
            new ConcurrentReferenceHashMap<>(256);

    public Collection<LogRecordOps> computeLogRecordOperations(
            Method method, Class<?> targetClass) {
        if (!Modifier.isPublic(method.getModifiers())) return Collections.emptyList();
        Method specificMethod =
                BridgeMethodResolver.findBridgedMethod(
                        ClassUtils.getMostSpecificMethod(method, targetClass));
        Set<LogRecordOps> result = new HashSet<>();
        result.addAll(parseLogRecordAnnotations(specificMethod));
        result.addAll(parseLogRecordsAnnotations(specificMethod));
        result.addAll(parseLogRecordAnnotations(getInterfaceMethodIfPossible(method)));
        result.addAll(parseLogRecordsAnnotations(getInterfaceMethodIfPossible(method)));
        return result;
    }

    private static Method getInterfaceMethodIfPossible(Method method) {
        if (!Modifier.isPublic(method.getModifiers()) || method.getDeclaringClass().isInterface()) {
            return method;
        }
        return INTERFACE_METHOD_CACHE.computeIfAbsent(
                method,
                key -> {
                    Class<?> current = key.getDeclaringClass();
                    while (current != null && current != Object.class) {
                        for (Class<?> ifc : current.getInterfaces()) {
                            try {
                                return ifc.getMethod(key.getName(), key.getParameterTypes());
                            } catch (NoSuchMethodException ex) {
                                // 继续查找
                            }
                        }
                        current = current.getSuperclass();
                    }
                    return key;
                });
    }

    private Collection<LogRecordOps> parseLogRecordsAnnotations(AnnotatedElement ae) {
        List<LogRecordOps> res = new ArrayList<>();
        AnnotatedElementUtils.findAllMergedAnnotations(ae, LogRecords.class)
                .forEach(
                        logRecords -> {
                            for (LogRecord logRecord : logRecords.value()) {
                                res.add(parseLogRecordAnnotation(ae, logRecord));
                            }
                        });
        return res;
    }

    private Collection<LogRecordOps> parseLogRecordAnnotations(AnnotatedElement ae) {
        List<LogRecordOps> ret = new ArrayList<>();
        AnnotatedElementUtils.findAllMergedAnnotations(ae, LogRecord.class)
                .forEach(annotation -> ret.add(parseLogRecordAnnotation(ae, annotation)));
        return ret;
    }

    private LogRecordOps parseLogRecordAnnotation(AnnotatedElement ae, LogRecord annotation) {
        LogRecordOps ops =
                LogRecordOps.builder()
                        .successLogTemplate(annotation.success())
                        .failLogTemplate(annotation.fail())
                        .type(annotation.type())
                        .bizNo(annotation.bizNo())
                        .operatorId(annotation.operator())
                        .subType(annotation.subType())
                        .extra(annotation.extra())
                        .condition(annotation.condition())
                        .isSuccess(annotation.successCondition())
                        .build();
        if (!StringUtils.hasText(ops.getSuccessLogTemplate())
                && !StringUtils.hasText(ops.getFailLogTemplate())) {
            throw new IllegalStateException(
                    "@LogRecord on '" + ae + "': 'success' 或 'fail' 至少需要配置一个");
        }
        return ops;
    }
}
