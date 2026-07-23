package com.xuejiai.aaf.framework.crud.definition;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** 创建、更新、关系 Patch 和具名自定义 UPDATE 命令的静态输入契约。 */
public record CrudMutationDefinition(
        Set<String> createFields,
        Set<String> updateFields,
        Map<String, String> relationFields,
        Map<String, Set<String>> customUpdateCommands) {

    public CrudMutationDefinition {
        createFields = normalized(createFields, "createFields");
        updateFields = normalized(updateFields, "updateFields");
        var copiedRelations = new LinkedHashMap<String, String>();
        Objects.requireNonNull(relationFields, "relationFields")
                .forEach(
                        (relationKey, inputField) ->
                                copiedRelations.put(
                                        requireText(relationKey, "relation key"),
                                        requireText(inputField, "relation input field")));
        relationFields = Map.copyOf(copiedRelations);

        var copiedCommands = new LinkedHashMap<String, Set<String>>();
        Objects.requireNonNull(customUpdateCommands, "customUpdateCommands")
                .forEach(
                        (commandType, fields) -> {
                            var normalizedFields = normalized(fields, "custom update fields");
                            if (normalizedFields.isEmpty()) {
                                throw new IllegalArgumentException("自定义 UPDATE 命令必须声明修改字段");
                            }
                            copiedCommands.put(
                                    requireText(commandType, "command type"), normalizedFields);
                        });
        customUpdateCommands = Map.copyOf(copiedCommands);
    }

    public CrudMutationDefinition(
            Set<String> createFields,
            Set<String> updateFields,
            Map<String, String> relationFields) {
        this(createFields, updateFields, relationFields, Map.of());
    }

    public static CrudMutationDefinition forTypes(CrudResourceTypeContract<?> types) {
        return new CrudMutationDefinition(
                typeFields(types.createType()), typeFields(types.updateType()), Map.of(), Map.of());
    }

    /** 返回叠加具名命令后的不可变契约；重复命令必须保持完全相同的字段声明。 */
    public CrudMutationDefinition withCustomUpdateCommands(
            Map<String, Set<String>> additionalCommands) {
        var merged = new LinkedHashMap<>(customUpdateCommands);
        Objects.requireNonNull(additionalCommands, "additionalCommands")
                .forEach(
                        (commandType, fields) -> {
                            var normalizedType = requireText(commandType, "command type");
                            var normalizedFields = normalized(fields, "custom update fields");
                            var existing = merged.putIfAbsent(normalizedType, normalizedFields);
                            if (existing != null && !existing.equals(normalizedFields)) {
                                throw new IllegalArgumentException(
                                        "自定义 UPDATE 命令重复且字段声明不一致: " + normalizedType);
                            }
                        });
        return new CrudMutationDefinition(createFields, updateFields, relationFields, merged);
    }

    /** 校验命令已在资源契约中声明，且调用方字段是该命令上限的非空子集。 */
    public void requireCustomUpdateCommand(String commandType, Set<String> modifiedFields) {
        var normalizedType = requireText(commandType, "command type");
        var expected = customUpdateCommands.get(normalizedType);
        var actual = normalized(modifiedFields, "modified fields");
        if (expected == null || actual.isEmpty() || !expected.containsAll(actual)) {
            throw new IllegalArgumentException("未声明或字段不匹配的自定义 UPDATE 命令: " + normalizedType);
        }
    }

    /** 返回所有具名命令声明的逻辑修改字段。 */
    public Set<String> customUpdateFields() {
        return customUpdateCommands.values().stream()
                .flatMap(Set::stream)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static Set<String> typeFields(Class<?> type) {
        if (Void.class.equals(type)) {
            return Set.of();
        }
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
}
