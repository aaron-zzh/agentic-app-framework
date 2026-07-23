package com.xuejiai.aaf.framework.crud.view;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** 全部激活 Loader 产生的不可变批量视图数据。 */
public final class CrudViewData {

    private static final CrudViewData EMPTY = new CrudViewData(Map.of());

    private final Map<String, Map<Long, Object>> values;

    private CrudViewData(Map<String, Map<Long, Object>> values) {
        this.values = values;
    }

    public static CrudViewData empty() {
        return EMPTY;
    }

    public static CrudViewData of(Map<String, ? extends Map<Long, ?>> values) {
        Objects.requireNonNull(values, "values");
        var copied = new LinkedHashMap<String, Map<Long, Object>>();
        values.forEach(
                (relationKey, relationValues) -> {
                    var byParent = new LinkedHashMap<Long, Object>();
                    Objects.requireNonNull(relationValues, "relationValues").forEach(byParent::put);
                    copied.put(relationKey, Map.copyOf(byParent));
                });
        return copied.isEmpty() ? EMPTY : new CrudViewData(Map.copyOf(copied));
    }

    public Map<Long, Object> relation(String relationKey) {
        return values.getOrDefault(relationKey, Map.of());
    }

    public Optional<Object> find(String relationKey, Long parentId) {
        return Optional.ofNullable(relation(relationKey).get(parentId));
    }

    public <T> Optional<T> find(String relationKey, Long parentId, Class<T> valueType) {
        return find(relationKey, parentId).map(valueType::cast);
    }
}
