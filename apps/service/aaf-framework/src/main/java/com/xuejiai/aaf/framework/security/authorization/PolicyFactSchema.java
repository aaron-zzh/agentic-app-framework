package com.xuejiai.aaf.framework.security.authorization;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** 策略可读取事实的编译期白名单。 */
public record PolicyFactSchema(Map<String, ValueType> facts) {

    private static final Map<String, ValueType> BUILT_INS =
            Map.of(
                    "operatorId", ValueType.NUMBER,
                    "subjectId", ValueType.NUMBER,
                    "tenantId", ValueType.NUMBER,
                    "workspaceId", ValueType.NUMBER,
                    "resource", ValueType.STRING,
                    "action", ValueType.STRING,
                    "objectId", ValueType.STRING);

    public PolicyFactSchema {
        var merged = new LinkedHashMap<>(BUILT_INS);
        if (facts != null) {
            facts.forEach(
                    (name, type) -> {
                        if (name == null
                                || !name.startsWith("attributes.")
                                || name.length() == "attributes.".length()) {
                            throw new IllegalArgumentException(
                                    "扩展策略事实必须使用 attributes.* 命名");
                        }
                        merged.put(name, Objects.requireNonNull(type, "type"));
                    });
        }
        facts = Map.copyOf(merged);
    }

    public static PolicyFactSchema builtInsOnly() {
        return new PolicyFactSchema(Map.of());
    }

    public ValueType requireType(String name) {
        var type = facts.get(name);
        if (type == null) {
            throw new PolicyCompilationException("未知策略事实: " + name);
        }
        return type;
    }

    public enum ValueType {
        STRING,
        NUMBER,
        BOOLEAN,
        STRING_ARRAY,
        NUMBER_ARRAY,
        BOOLEAN_ARRAY;

        public boolean array() {
            return this == STRING_ARRAY || this == NUMBER_ARRAY || this == BOOLEAN_ARRAY;
        }

        public ValueType elementType() {
            return switch (this) {
                case STRING_ARRAY -> STRING;
                case NUMBER_ARRAY -> NUMBER;
                case BOOLEAN_ARRAY -> BOOLEAN;
                default -> this;
            };
        }
    }
}
