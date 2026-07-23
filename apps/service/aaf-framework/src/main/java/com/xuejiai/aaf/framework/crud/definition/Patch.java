package com.xuejiai.aaf.framework.crud.definition;

import java.util.Objects;
import java.util.function.Function;

import tools.jackson.databind.JsonNode;

/** 部分更新的统一三态值：未传、显式清空或设置值。 */
public sealed interface Patch<T> permits Patch.Absent, Patch.NullValue, Patch.Value {

    record Absent<T>() implements Patch<T> {}

    record NullValue<T>() implements Patch<T> {}

    record Value<T>(T value) implements Patch<T> {
        public Value {
            Objects.requireNonNull(value, "value");
        }
    }

    static <T> Patch<T> absent() {
        return new Absent<>();
    }

    static <T> Patch<T> nullValue() {
        return new NullValue<>();
    }

    static <T> Patch<T> value(T value) {
        return new Value<>(value);
    }

    /** Java null 节点表示属性未出现，JSON null 表示显式清空。 */
    static <T> Patch<T> parse(JsonNode node, Function<JsonNode, T> decoder) {
        Objects.requireNonNull(decoder, "decoder");
        if (node == null) {
            return absent();
        }
        if (node.isNull()) {
            return nullValue();
        }
        return value(decoder.apply(node));
    }

    default boolean isAbsent() {
        return this instanceof Absent<?>;
    }

    default boolean isNullValue() {
        return this instanceof NullValue<?>;
    }

    @SuppressWarnings("unchecked")
    default T valueOrNull() {
        return this instanceof Value<?> value ? (T) value.value() : null;
    }
}
