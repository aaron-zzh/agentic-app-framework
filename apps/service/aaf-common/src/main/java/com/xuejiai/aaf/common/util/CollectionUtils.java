package com.xuejiai.aaf.common.util;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 集合转换工具。
 *
 * <p>空来源集合返回空集合；映射函数不能为空。所有转换结果均不可修改。
 */
public final class CollectionUtils {

    private CollectionUtils() {}

    /**
     * 将集合映射为列表，忽略映射结果中的空值。
     *
     * @param source 来源集合，可为空
     * @param mapper 映射函数
     * @param <T> 来源元素类型
     * @param <R> 结果元素类型
     * @return 保持来源迭代顺序的不可修改列表
     */
    public static <T, R> List<R> mapToList(
            Collection<? extends T> source, Function<? super T, ? extends R> mapper) {
        Objects.requireNonNull(mapper, "mapper 不能为空");
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        return source.stream().<R>map(mapper).filter(Objects::nonNull).toList();
    }

    /**
     * 将集合映射为集合，忽略映射结果中的空值。
     *
     * @param source 来源集合，可为空
     * @param mapper 映射函数
     * @param <T> 来源元素类型
     * @param <R> 结果元素类型
     * @return 去重且保持首次出现顺序的不可修改集合
     */
    public static <T, R> Set<R> mapToSet(
            Collection<? extends T> source, Function<? super T, ? extends R> mapper) {
        Objects.requireNonNull(mapper, "mapper 不能为空");
        if (source == null || source.isEmpty()) {
            return Set.of();
        }
        Set<R> result =
                source.stream()
                        .map(mapper)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        return Collections.unmodifiableSet(result);
    }

    /**
     * 将集合映射为键值对。
     *
     * @param source 来源集合，可为空
     * @param keyMapper 键映射函数
     * @param valueMapper 值映射函数
     * @param <T> 来源元素类型
     * @param <K> 键类型
     * @param <V> 值类型
     * @return 保持来源迭代顺序的不可修改映射
     * @throws IllegalArgumentException 映射后存在重复键时抛出
     */
    public static <T, K, V> Map<K, V> mapToMap(
            Collection<? extends T> source,
            Function<? super T, ? extends K> keyMapper,
            Function<? super T, ? extends V> valueMapper) {
        Objects.requireNonNull(keyMapper, "keyMapper 不能为空");
        Objects.requireNonNull(valueMapper, "valueMapper 不能为空");
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<K, V> result =
                source.stream()
                        .collect(
                                Collectors.toMap(
                                        keyMapper,
                                        valueMapper,
                                        (first, second) -> {
                                            throw new IllegalArgumentException("映射键重复");
                                        },
                                        LinkedHashMap::new));
        return Collections.unmodifiableMap(result);
    }
}
