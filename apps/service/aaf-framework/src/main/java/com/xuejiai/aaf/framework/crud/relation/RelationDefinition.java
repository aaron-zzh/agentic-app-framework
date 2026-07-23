package com.xuejiai.aaf.framework.crud.relation;

import java.util.Objects;
import java.util.regex.Pattern;

import com.xuejiai.aaf.framework.crud.definition.ResourceKey;

/** 编译后的关系存储、输入输出和附加授权契约。 */
public record RelationDefinition<E, P>(
        String key,
        ResourceKey targetResource,
        Cardinality cardinality,
        SyncMode syncMode,
        int maxCardinality,
        Class<?> associationEntity,
        AssociationKind associationKind,
        String sourceProperty,
        String targetProperty,
        String inputField,
        String viewField,
        String additionalPolicyBean) {

    private static final Pattern KEY_PATTERN =
            Pattern.compile("^[a-z][a-zA-Z0-9]*(?:[._-][a-zA-Z0-9]+)*$");

    public RelationDefinition {
        key = requireKey(key);
        targetResource = Objects.requireNonNull(targetResource, "targetResource");
        cardinality = Objects.requireNonNull(cardinality, "cardinality");
        syncMode = Objects.requireNonNull(syncMode, "syncMode");
        associationEntity = Objects.requireNonNull(associationEntity, "associationEntity");
        associationKind = Objects.requireNonNull(associationKind, "associationKind");
        sourceProperty = requireText(sourceProperty, "sourceProperty");
        targetProperty = requireText(targetProperty, "targetProperty");
        inputField = optionalText(inputField);
        viewField = optionalText(viewField);
        additionalPolicyBean = optionalText(additionalPolicyBean);
        if (maxCardinality <= 0 || cardinality == Cardinality.ONE && maxCardinality != 1) {
            throw new IllegalArgumentException("关系最大基数非法: " + maxCardinality);
        }
        if (associationKind == AssociationKind.ONE_TO_MANY_CHILD && syncMode != SyncMode.READ_ONLY) {
            throw new IllegalArgumentException("ONE_TO_MANY_CHILD 仅支持 READ_ONLY");
        }
        if (syncMode != SyncMode.READ_ONLY && inputField.isBlank()) {
            throw new IllegalArgumentException("可写关系必须声明 inputField");
        }
    }

    private static String requireKey(String value) {
        var key = requireText(value, "relation key");
        if (!KEY_PATTERN.matcher(key).matches()) {
            throw new IllegalArgumentException("非法 relation key: " + key);
        }
        return key;
    }

    private static String requireText(String value, String name) {
        var normalized = optionalText(value);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
        return normalized;
    }

    private static String optionalText(String value) {
        return value == null ? "" : value.trim();
    }

    public enum Cardinality {
        ONE,
        MANY
    }

    public enum SyncMode {
        READ_ONLY,
        REPLACE,
        DIFF
    }
}
