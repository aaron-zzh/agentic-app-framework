package com.xuejiai.aaf.framework.crud.enforcement;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.data.jpa.domain.Specification;

import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.framework.crud.definition.FieldCapability;
import com.xuejiai.aaf.framework.security.authorization.AuthorizationConstraint;

/** CRUD PEP 可消费的 L3 约束；记录范围保持为数据库侧 Specification。 */
public record CrudDataAuthorizationConstraint(
        String resourceKey,
        Specification<?> recordScope,
        Map<FieldCapability, Set<String>> deniedFields,
        String accessVersion)
        implements AuthorizationConstraint {

    public CrudDataAuthorizationConstraint {
        if (resourceKey == null || resourceKey.isBlank()) {
            throw new IllegalArgumentException("resourceKey 不能为空");
        }
        recordScope = Objects.requireNonNull(recordScope, "recordScope");
        accessVersion = Objects.requireNonNull(accessVersion, "accessVersion");
        if (accessVersion.isBlank()) {
            throw new IllegalArgumentException("accessVersion 不能为空");
        }
        deniedFields = normalizeDeniedFields(deniedFields);
    }

    @SuppressWarnings("unchecked")
    public <E extends BaseEntity> Specification<E> typedRecordScope() {
        return (Specification<E>) recordScope;
    }

    private static Map<FieldCapability, Set<String>> normalizeDeniedFields(
            Map<FieldCapability, Set<String>> deniedFields) {
        Objects.requireNonNull(deniedFields, "deniedFields");
        if (deniedFields.keySet().stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("字段拒绝能力不能为空");
        }
        var normalized = new EnumMap<FieldCapability, Set<String>>(FieldCapability.class);
        for (var capability : FieldCapability.values()) {
            var fields = deniedFields.getOrDefault(capability, Set.of());
            if (fields == null
                    || fields.stream().anyMatch(field -> field == null || field.isBlank())) {
                throw new IllegalArgumentException("字段拒绝集合不完整");
            }
            normalized.put(capability, Set.copyOf(fields));
        }
        return Map.copyOf(normalized);
    }
}
