package com.xuejiai.aaf.common.util;

import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** Map 工具类。 */
public final class MapUtils {

    private MapUtils() {}

    /**
     * 查找指定键对应的非空值并执行后续处理。
     *
     * @param map 来源映射，可为空
     * @param key 查找键，可为空
     * @param consumer 值消费者
     * @param <K> 键类型
     * @param <V> 值类型
     */
    public static <K, V> void findAndThen(Map<K, V> map, K key, Consumer<? super V> consumer) {
        Objects.requireNonNull(consumer, "consumer 不能为空");
        if (map == null || key == null) {
            return;
        }
        var value = map.get(key);
        if (value != null) {
            consumer.accept(value);
        }
    }
}
