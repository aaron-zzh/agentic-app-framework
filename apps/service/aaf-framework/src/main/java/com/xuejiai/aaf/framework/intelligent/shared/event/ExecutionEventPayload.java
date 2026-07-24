package com.xuejiai.aaf.framework.intelligent.shared.event;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 已脱敏、可安全序列化的执行事件载荷。 */
public record ExecutionEventPayload(Map<String, Object> values) {

    private static final int MAX_NESTING_DEPTH = 4;
    private static final int MAX_KEY_LENGTH = 128;

    public ExecutionEventPayload {
        values = freezeMap(values == null ? Map.of() : values, 0);
    }

    /** 创建空载荷。 */
    public static ExecutionEventPayload empty() {
        return new ExecutionEventPayload(Map.of());
    }

    private static Map<String, Object> freezeMap(Map<?, ?> source, int depth) {
        if (depth > MAX_NESTING_DEPTH) {
            throw new IllegalArgumentException("payload 嵌套深度不能超过 " + MAX_NESTING_DEPTH);
        }

        var copy = new LinkedHashMap<String, Object>(source.size());
        for (var entry : source.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new IllegalArgumentException("payload key 必须是字符串");
            }
            validateKey(key);
            copy.put(key, freezeValue(entry.getValue(), depth + 1));
        }
        return Collections.unmodifiableMap(copy);
    }

    private static List<Object> freezeList(List<?> source, int depth) {
        if (depth > MAX_NESTING_DEPTH) {
            throw new IllegalArgumentException("payload 嵌套深度不能超过 " + MAX_NESTING_DEPTH);
        }

        var copy = new ArrayList<>(source.size());
        for (var value : source) {
            copy.add(freezeValue(value, depth + 1));
        }
        return Collections.unmodifiableList(copy);
    }

    private static Object freezeValue(Object value, int depth) {
        if (value == null || value instanceof String || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Byte
                || value instanceof Short
                || value instanceof Integer
                || value instanceof Long
                || value instanceof BigInteger
                || value instanceof BigDecimal) {
            return value;
        }
        if (value instanceof Double number) {
            if (!Double.isFinite(number)) {
                throw new IllegalArgumentException("payload 不允许非有限浮点数");
            }
            return number;
        }
        if (value instanceof Float number) {
            if (!Float.isFinite(number)) {
                throw new IllegalArgumentException("payload 不允许非有限浮点数");
            }
            return number;
        }
        if (value instanceof Map<?, ?> map) {
            return freezeMap(map, depth);
        }
        if (value instanceof List<?> list) {
            return freezeList(list, depth);
        }
        throw new IllegalArgumentException(
                "payload 仅允许 JSON 值，实际类型: " + value.getClass().getName());
    }

    private static void validateKey(String key) {
        if (key.isBlank()) {
            throw new IllegalArgumentException("payload key 不能为空白");
        }
        if (!key.equals(key.trim())) {
            throw new IllegalArgumentException("payload key 不能包含首尾空白");
        }
        if (key.length() > MAX_KEY_LENGTH) {
            throw new IllegalArgumentException("payload key 长度不能超过 " + MAX_KEY_LENGTH);
        }

        var normalized = key.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
        if (normalized.contains("password")
                || normalized.endsWith("secret")
                || normalized.endsWith("credential")
                || normalized.endsWith("credentials")
                || normalized.endsWith("token")
                || normalized.equals("authorization")
                || normalized.equals("authorizationheader")
                || normalized.endsWith("cookie")
                || normalized.endsWith("apikey")
                || normalized.equals("systemprompt")) {
            throw new IllegalArgumentException("payload 禁止包含敏感字段: " + key);
        }
    }
}
