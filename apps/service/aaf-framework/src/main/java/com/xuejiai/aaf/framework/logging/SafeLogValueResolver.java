package com.xuejiai.aaf.framework.logging;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.aop.support.AopUtils;
import org.springframework.core.DefaultParameterNameDiscoverer;

import com.xuejiai.aaf.framework.bizlog.context.LogRecordContext;

/**
 * 安全日志模板值解析器。
 *
 * <p>仅允许读取方法参数、日志上下文、返回值和错误信息的只读属性路径；除兼容已有模板的 {@code size()} 外，不执行模板中的任何方法。
 */
public final class SafeLogValueResolver {

    private static final Pattern SAFE_PATH =
            Pattern.compile(
                    "#?[A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*(?:\\(\\))?)*");
    private static final DefaultParameterNameDiscoverer PARAMETER_NAMES =
            new DefaultParameterNameDiscoverer();

    /** 创建当前方法的固定变量表。 */
    public Map<String, Object> createVariables(
            Method method,
            Object[] args,
            Class<?> targetClass,
            Object result,
            String errorMessage) {
        var variables = new HashMap<String, Object>();
        var globalVariables = LogRecordContext.getGlobalVariableMap();
        if (globalVariables != null) {
            variables.putAll(globalVariables);
        }

        var targetMethod = AopUtils.getMostSpecificMethod(method, targetClass);
        var parameterNames = PARAMETER_NAMES.getParameterNames(targetMethod);
        for (int index = 0; index < args.length; index++) {
            var value = args[index];
            variables.put("arg" + index, value);
            variables.put("a" + index, value);
            variables.put("p" + index, value);
            if (parameterNames != null && index < parameterNames.length) {
                variables.put(parameterNames[index], value);
            }
        }

        var methodVariables = LogRecordContext.getVariables();
        if (methodVariables != null) {
            variables.putAll(methodVariables);
        }
        variables.put("_ret", result);
        variables.put("result", result);
        variables.put("_errorMsg", errorMessage);
        return variables;
    }

    /** 解析固定变量或只读属性路径。 */
    public Object resolve(String expression, Map<String, Object> variables) {
        var path = expression == null ? "" : expression.trim();
        if (!SAFE_PATH.matcher(path).matches()) {
            throw new IllegalArgumentException("日志模板包含不安全表达式: " + expression);
        }
        if (path.charAt(0) == '#') {
            path = path.substring(1);
        }

        var segments = path.split("\\.");
        Object value = variables.get(segments[0]);
        for (int index = 1; index < segments.length; index++) {
            value = readSegment(value, segments[index]);
        }
        return value;
    }

    private Object readSegment(Object target, String segment) {
        if (target == null) {
            return null;
        }
        if ("size".equals(segment) || "size()".equals(segment)) {
            return sizeOf(target);
        }
        var property = segment.endsWith("()") ? segment.substring(0, segment.length() - 2) : segment;
        if (target instanceof Map<?, ?> map) {
            return map.get(property);
        }
        return readProperty(target, property);
    }

    private Object sizeOf(Object target) {
        return switch (target) {
            case Collection<?> collection -> collection.size();
            case Map<?, ?> map -> map.size();
            case CharSequence text -> text.length();
            default -> {
                if (target.getClass().isArray()) {
                    yield Array.getLength(target);
                }
                throw new IllegalArgumentException("日志模板 size 仅支持集合、映射、数组和字符串");
            }
        };
    }

    private Object readProperty(Object target, String property) {
        if (target.getClass().isRecord()) {
            for (var component : target.getClass().getRecordComponents()) {
                if (component.getName().equals(property)) {
                    return invokeAccessor(target, component.getAccessor());
                }
            }
        }
        try {
            for (var descriptor : Introspector.getBeanInfo(target.getClass()).getPropertyDescriptors()) {
                if (descriptor.getName().equals(property) && descriptor.getReadMethod() != null) {
                    return invokeAccessor(target, descriptor.getReadMethod());
                }
            }
        } catch (IntrospectionException exception) {
            throw new IllegalArgumentException("无法读取日志模板属性: " + property, exception);
        }
        throw new IllegalArgumentException("日志模板属性不存在: " + property);
    }

    private Object invokeAccessor(Object target, Method accessor) {
        try {
            return accessor.invoke(target);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalArgumentException("无法读取日志模板属性: " + accessor.getName(), exception);
        }
    }
}
