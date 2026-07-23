package com.xuejiai.aaf.framework.crud.definition;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.xuejiai.aaf.framework.crud.dto.ResourceRefDTO;

/** 输出字段集与可选展示映射器的静态契约。 */
public record CrudViewDefinition(Map<String, Set<String>> fieldSets, String viewMapperBean) {

    private static final List<String> STANDARD_FIELD_SETS =
            List.of("list", "detail", "picker", "export");

    public CrudViewDefinition {
        fieldSets = copySets(fieldSets, "fieldSets");
        if (fieldSets.isEmpty()) {
            throw new IllegalArgumentException("资源字段集不能为空");
        }
        viewMapperBean = viewMapperBean == null ? "" : viewMapperBean.trim();
    }

    public static CrudViewDefinition forTypes(CrudResourceTypeContract<?> types) {
        return forFieldSets(types, STANDARD_FIELD_SETS);
    }

    public static CrudViewDefinition optionsOnly(CrudResourceTypeContract<?> types) {
        return forFieldSets(types, List.of("picker"));
    }

    /** 返回替换指定字段集后的新视图定义，未指定字段集继续使用当前默认。 */
    public CrudViewDefinition withFieldSet(String name, Set<String> fields) {
        var customized = new LinkedHashMap<>(fieldSets);
        customized.put(requireText(name, "fieldSet"), normalized(fields, "fieldSet"));
        return new CrudViewDefinition(customized, viewMapperBean);
    }

    /** 返回替换指定字段集后的新视图定义。 */
    public CrudViewDefinition withFieldSet(String name, String... fields) {
        return withFieldSet(name, Set.of(fields));
    }

    private static CrudViewDefinition forFieldSets(
            CrudResourceTypeContract<?> types, List<String> names) {
        var outputFields = typeFields(types.viewType());
        var referenceFields = referenceFields(types.viewType());
        var scalarFields = new LinkedHashSet<>(outputFields);
        scalarFields.removeAll(referenceFields);
        var fieldSets = new LinkedHashMap<String, Set<String>>();
        names.forEach(
                fieldSet ->
                        fieldSets.put(
                                fieldSet,
                                "detail".equals(fieldSet) ? outputFields : Set.copyOf(scalarFields)));
        return new CrudViewDefinition(fieldSets, "");
    }

    private static Map<String, Set<String>> copySets(Map<String, Set<String>> values, String name) {
        Objects.requireNonNull(values, name);
        var copy = new LinkedHashMap<String, Set<String>>();
        values.forEach(
                (key, items) -> copy.put(requireText(key, name + " key"), normalized(items, name)));
        return Map.copyOf(copy);
    }

    private static Set<String> typeFields(Class<?> type) {
        var fields = new LinkedHashSet<String>();
        if (type.isRecord()) {
            Arrays.stream(type.getRecordComponents())
                    .map(component -> component.getName())
                    .forEach(fields::add);
        } else {
            for (var current = type;
                    current != null && !Object.class.equals(current);
                    current = current.getSuperclass()) {
                Arrays.stream(current.getDeclaredFields())
                        .filter(field -> !Modifier.isStatic(field.getModifiers()))
                        .filter(field -> !field.isSynthetic())
                        .map(Field::getName)
                        .forEach(fields::add);
            }
        }
        return Set.copyOf(fields);
    }

    private static Set<String> normalized(Set<String> values, String name) {
        Objects.requireNonNull(values, name);
        var normalized = new LinkedHashSet<String>();
        values.forEach(value -> normalized.add(requireText(value, name)));
        return Set.copyOf(normalized);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
        return value.trim();
    }

    private static Set<String> referenceFields(Class<?> type) {
        var fields = new LinkedHashSet<String>();
        if (type.isRecord()) {
            Arrays.stream(type.getRecordComponents())
                    .filter(component -> isReferenceType(component.getGenericType()))
                    .map(component -> component.getName())
                    .forEach(fields::add);
            return Set.copyOf(fields);
        }
        for (var current = type;
                current != null && !Object.class.equals(current);
                current = current.getSuperclass()) {
            Arrays.stream(current.getDeclaredFields())
                    .filter(field -> !Modifier.isStatic(field.getModifiers()))
                    .filter(field -> !field.isSynthetic())
                    .filter(field -> isReferenceType(field.getGenericType()))
                    .map(Field::getName)
                    .forEach(fields::add);
        }
        return Set.copyOf(fields);
    }

    private static boolean isReferenceType(Type type) {
        if (type instanceof Class<?> valueType) {
            return ResourceRefDTO.class.isAssignableFrom(valueType);
        }
        if (!(type instanceof ParameterizedType parameterized)
                || !(parameterized.getRawType() instanceof Class<?> rawType)
                || !Collection.class.isAssignableFrom(rawType)) {
            return false;
        }
        return Arrays.stream(parameterized.getActualTypeArguments())
                .anyMatch(
                        argument ->
                                argument instanceof Class<?> valueType
                                        && ResourceRefDTO.class.isAssignableFrom(valueType));
    }
}
