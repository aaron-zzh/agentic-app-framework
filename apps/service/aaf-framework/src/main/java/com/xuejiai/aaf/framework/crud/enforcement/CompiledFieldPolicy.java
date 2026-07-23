package com.xuejiai.aaf.framework.crud.enforcement;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.crud.definition.FieldCapability;

/** 启动期字段能力上限叠加当前主体动态收窄后的不可变策略。 */
public record CompiledFieldPolicy(Map<String, Set<FieldCapability>> capabilities) {

    public CompiledFieldPolicy {
        var copy = new LinkedHashMap<String, Set<FieldCapability>>();
        capabilities.forEach((field, values) -> copy.put(field, Set.copyOf(values)));
        capabilities = Map.copyOf(copy);
    }

    public boolean allows(String field, FieldCapability capability) {
        return capabilities.getOrDefault(field, Set.of()).contains(capability);
    }

    public void require(String field, FieldCapability capability) {
        if (field == null || field.isBlank() || !allows(field, capability)) {
            throw exception(GlobalErrorCode.FORBIDDEN);
        }
    }

    public void requireAll(Iterable<String> fields, FieldCapability capability) {
        fields.forEach(field -> require(field, capability));
    }

    public Set<String> fields(FieldCapability capability) {
        return capabilities.entrySet().stream()
                .filter(entry -> entry.getValue().contains(capability))
                .map(Map.Entry::getKey)
                .collect(Collectors.toUnmodifiableSet());
    }

    public CompiledFieldPolicy deny(Map<FieldCapability, Set<String>> denied) {
        var restricted = new LinkedHashMap<String, Set<FieldCapability>>();
        capabilities.forEach(
                (field, values) -> {
                    var allowed = EnumSet.noneOf(FieldCapability.class);
                    allowed.addAll(values);
                    denied.forEach(
                            (capability, fields) -> {
                                if (fields.contains(field)) {
                                    allowed.remove(capability);
                                }
                            });
                    restricted.put(field, Set.copyOf(allowed));
                });
        var unknown =
                denied.values().stream()
                        .flatMap(Set::stream)
                        .filter(field -> !capabilities.containsKey(field))
                        .findFirst();
        if (unknown.isPresent()) {
            throw exception(GlobalErrorCode.FORBIDDEN);
        }
        return new CompiledFieldPolicy(restricted);
    }
}
