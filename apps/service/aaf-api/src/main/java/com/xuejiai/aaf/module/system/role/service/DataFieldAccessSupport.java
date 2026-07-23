package com.xuejiai.aaf.module.system.role.service;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.crud.definition.FieldCapability;
import com.xuejiai.aaf.framework.security.authorization.FieldAccessSupport;
import com.xuejiai.aaf.module.system.auth.vo.FieldAccessVO;
import com.xuejiai.aaf.module.system.role.domain.Permission;
import com.xuejiai.aaf.module.system.role.repository.PermissionRepository;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.type.TypeReference;

/** 基于 sys_permission.field_access 的字段能力动态收窄实现。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DataFieldAccessSupport implements FieldAccessSupport {

    private final PermissionRepository permissionRepository;

    @Override
    public Map<FieldCapability, Set<String>> deniedFields(String entitySlug, Long userId) {
        if (entitySlug == null || entitySlug.isBlank() || userId == null) {
            throw exception(GlobalErrorCode.FORBIDDEN);
        }
        var fields = new HashMap<String, FieldAccessVO>();
        for (Permission permission :
                permissionRepository.findByUserIdAndEntitySlug(userId, entitySlug)) {
            if (permission.getFieldAccess() == null || permission.getFieldAccess().isBlank()) {
                continue;
            }
            Map<String, FieldAccessVO> parsed;
            try {
                parsed =
                        JsonUtils.parseObject(
                                permission.getFieldAccess(), new TypeReference<>() {});
            } catch (RuntimeException cause) {
                throw exception(GlobalErrorCode.FORBIDDEN);
            }
            if (parsed == null
                    || parsed.entrySet().stream()
                            .anyMatch(
                                    entry ->
                                            entry.getKey() == null
                                                    || entry.getKey().isBlank()
                                                    || entry.getValue() == null)) {
                throw exception(GlobalErrorCode.FORBIDDEN);
            }
            parsed.forEach(
                    (field, access) ->
                            fields.merge(
                                    field,
                                    access,
                                    (existing, incoming) ->
                                            new FieldAccessVO(
                                                    existing.visible() || incoming.visible(),
                                                    existing.editable() || incoming.editable())));
        }

        var denied = new EnumMap<FieldCapability, Set<String>>(FieldCapability.class);
        for (var capability : FieldCapability.values()) {
            denied.put(capability, new HashSet<>());
        }
        fields.forEach(
                (field, access) -> {
                    if (!access.visible()) {
                        denied.get(FieldCapability.READ).add(field);
                        denied.get(FieldCapability.FILTER).add(field);
                        denied.get(FieldCapability.SORT).add(field);
                        denied.get(FieldCapability.AGGREGATE).add(field);
                        denied.get(FieldCapability.EXPORT).add(field);
                        denied.get(FieldCapability.REFERENCE).add(field);
                    }
                    if (!access.editable()) {
                        denied.get(FieldCapability.WRITE).add(field);
                        denied.get(FieldCapability.REFERENCE).add(field);
                    }
                });
        var immutable = new EnumMap<FieldCapability, Set<String>>(FieldCapability.class);
        denied.forEach((capability, values) -> immutable.put(capability, Set.copyOf(values)));
        return Map.copyOf(immutable);
    }
}
