package com.xuejiai.aaf.framework.bizlog.context;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import org.springframework.util.CollectionUtils;

/**
 * 操作日志 SpEL 上下文，支持方法内手动注入额外变量。
 *
 * <p>使用方式：在被 @LogRecord 标注的方法内调用 {@code LogRecordContext.putVariable("key", value)}， 即可在模板中通过
 * {@code {{#key}}} 引用。
 *
 * <p>使用 {@link InheritableThreadLocal} 保证父子线程传递， 内部用 Deque 支持方法嵌套调用时的作用域隔离。
 */
public class LogRecordContext {

    private static final InheritableThreadLocal<Deque<Map<String, Object>>> VARIABLE_MAP_STACK =
            new InheritableThreadLocal<>();

    private static final InheritableThreadLocal<Map<String, Object>> GLOBAL_VARIABLE_MAP =
            new InheritableThreadLocal<>();

    private LogRecordContext() {
        throw new IllegalStateException("Utility class");
    }

    /** 向当前方法作用域注入变量。 */
    public static void putVariable(String name, Object value) {
        if (VARIABLE_MAP_STACK.get() == null) {
            Deque<Map<String, Object>> stack = new ArrayDeque<>();
            VARIABLE_MAP_STACK.set(stack);
        }
        if (VARIABLE_MAP_STACK.get().isEmpty()) {
            VARIABLE_MAP_STACK.get().push(new HashMap<>());
        }
        VARIABLE_MAP_STACK.get().element().put(name, value);
    }

    /** 向全局作用域注入变量（跨方法调用链可见，优先级低于方法作用域）。 */
    public static void putGlobalVariable(String name, Object value) {
        if (GLOBAL_VARIABLE_MAP.get() == null) {
            GLOBAL_VARIABLE_MAP.set(new HashMap<>());
        }
        GLOBAL_VARIABLE_MAP.get().put(name, value);
    }

    public static Object getVariable(String key) {
        Map<String, Object> variableMap = VARIABLE_MAP_STACK.get().peek();
        return variableMap == null ? null : variableMap.get(key);
    }

    /** 先查方法作用域，再查全局作用域。 */
    public static Object getMethodOrGlobal(String key) {
        Object result = null;
        Map<String, Object> variableMap =
                VARIABLE_MAP_STACK.get() != null ? VARIABLE_MAP_STACK.get().peek() : null;
        if (!CollectionUtils.isEmpty(variableMap) && (result = variableMap.get(key)) != null) {
            return result;
        }
        Map<String, Object> globalMap = GLOBAL_VARIABLE_MAP.get();
        if (!CollectionUtils.isEmpty(globalMap)) {
            return globalMap.get(key);
        }
        return result;
    }

    public static Map<String, Object> getVariables() {
        Deque<Map<String, Object>> mapStack = VARIABLE_MAP_STACK.get();
        return mapStack == null ? new HashMap<>() : mapStack.peek();
    }

    public static Map<String, Object> getGlobalVariableMap() {
        return GLOBAL_VARIABLE_MAP.get();
    }

    /** 清除当前方法作用域（方法执行完成后由框架调用，业务方不需要调用）。 */
    public static void clear() {
        if (VARIABLE_MAP_STACK.get() != null) {
            VARIABLE_MAP_STACK.get().pop();
        }
    }

    public static void clearGlobal() {
        if (GLOBAL_VARIABLE_MAP.get() != null) {
            GLOBAL_VARIABLE_MAP.get().clear();
        }
    }

    /** 每进入一个方法时压入一个空 span，方法结束后 pop（由框架调用）。 */
    public static void putEmptySpan() {
        if (VARIABLE_MAP_STACK.get() == null) {
            VARIABLE_MAP_STACK.set(new ArrayDeque<>());
        }
        VARIABLE_MAP_STACK.get().push(new HashMap<>());

        if (GLOBAL_VARIABLE_MAP.get() == null) {
            GLOBAL_VARIABLE_MAP.set(new HashMap<>());
        }
    }
}
