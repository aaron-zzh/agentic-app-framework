package com.xuejiai.aaf.framework.crud.view;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 单个 fieldSet 编译后的输出字段、关系依赖和视图映射器计划。 */
public record CrudViewPlan(
        String fieldSet,
        Set<String> outputFields,
        Map<String, Set<String>> dependencies,
        String viewMapperBean) {

    public CrudViewPlan {
        if (fieldSet == null || fieldSet.isBlank()) {
            throw new IllegalArgumentException("fieldSet 不能为空");
        }
        fieldSet = fieldSet.trim();
        outputFields = Set.copyOf(Objects.requireNonNull(outputFields, "outputFields"));
        var copiedDependencies = new LinkedHashMap<String, Set<String>>();
        Objects.requireNonNull(dependencies, "dependencies")
                .forEach(
                        (field, relationKeys) ->
                                copiedDependencies.put(field, Set.copyOf(relationKeys)));
        dependencies = Map.copyOf(copiedDependencies);
        viewMapperBean = viewMapperBean == null ? "" : viewMapperBean.trim();
    }

    public Set<String> relationKeys() {
        var keys = new LinkedHashSet<String>();
        dependencies.values().forEach(keys::addAll);
        return Set.copyOf(keys);
    }

    /** 动态字段策略先收窄输出，再据此裁剪 Loader 依赖。 */
    public CrudViewPlan restrictTo(Set<String> allowedFields) {
        var allowed = new LinkedHashSet<>(outputFields);
        allowed.retainAll(allowedFields);
        var restrictedDependencies = new LinkedHashMap<String, Set<String>>();
        dependencies.forEach(
                (field, relationKeys) -> {
                    if (allowed.contains(field)) {
                        restrictedDependencies.put(field, relationKeys);
                    }
                });
        return new CrudViewPlan(fieldSet, allowed, restrictedDependencies, viewMapperBean);
    }

    public boolean usesViewMapper() {
        return !viewMapperBean.isBlank();
    }
}
