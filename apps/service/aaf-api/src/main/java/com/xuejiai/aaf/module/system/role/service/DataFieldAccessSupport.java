package com.xuejiai.aaf.module.system.role.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.xuejiai.aaf.framework.security.access.FieldAccessSupport;
import com.xuejiai.aaf.module.system.auth.vo.FieldAccessVO;
import com.xuejiai.aaf.module.system.role.domain.Permission;
import com.xuejiai.aaf.module.system.role.repository.PermissionRepository;

import lombok.RequiredArgsConstructor;

/** 基于 sys_permission.field_access 的字段级权限裁剪支持。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DataFieldAccessSupport implements FieldAccessSupport {

    private final PermissionRepository permissionRepository;
    private final ObjectMapper objectMapper;

    @Override
    public Set<String> hiddenFields(String entitySlug, Long userId, String action) {
        if (entitySlug == null || userId == null) {
            return Set.of();
        }
        var fields = new HashMap<String, FieldAccessVO>();
        for (Permission permission : permissionRepository.findByUserIdAndEntitySlug(userId, entitySlug)) {
            if (permission.getFieldAccess() == null || permission.getFieldAccess().isBlank()) {
                continue;
            }
            try {
                Map<String, FieldAccessVO> parsed =
                        objectMapper.readValue(permission.getFieldAccess(), new TypeReference<>() {});
                parsed.forEach(
                        (field, access) ->
                                fields.merge(
                                        field,
                                        access,
                                        (existing, incoming) ->
                                                new FieldAccessVO(
                                                        existing.visible() || incoming.visible(),
                                                        existing.editable() || incoming.editable())));
            } catch (Exception ignored) {
                // 字段权限配置错误时不在响应层泄露异常，保留服务日志由上游配置校验处理。
            }
        }
        return fields.entrySet().stream()
                .filter(entry -> entry.getValue() != null && !entry.getValue().visible())
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }
}
