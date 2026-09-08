package com.xuejiai.aaf.module.system.role.service;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.module.system.auth.vo.FieldAccessVO;
import com.xuejiai.aaf.module.system.entity.vo.EntityAccessVO;
import com.xuejiai.aaf.module.system.permission.service.PermissionSecurityService;
import com.xuejiai.aaf.module.system.role.domain.Permission;
import com.xuejiai.aaf.module.system.role.repository.PermissionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.type.TypeReference;

/**
 * 权限计算服务。
 *
 * @author AaronZZH & Kiro
 */
@Slf4j
@Service("rolePermissionService")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PermissionService {

    private final PermissionRepository permissionRepository;
    private final PermissionSecurityService permissionSecurityService;

    /** 计算用户对指定实体的权限：正式权限码决定 CRUD，旧实体权限仅补充字段级配置。 */
    public EntityAccessVO getEntityAccess(Long userId, String entitySlug) {
        var permissions = permissionRepository.findByUserIdAndEntitySlug(userId, entitySlug);
        var authorityCodes = permissionSecurityService.authorityCodes(userId);
        var actionPrefix = ":" + entitySlug + ":";

        boolean read =
                authorityCodes.stream().anyMatch(code -> code.endsWith(actionPrefix + "read"));
        boolean create =
                authorityCodes.stream().anyMatch(code -> code.endsWith(actionPrefix + "create"));
        boolean update =
                authorityCodes.stream().anyMatch(code -> code.endsWith(actionPrefix + "update"));
        boolean delete =
                authorityCodes.stream().anyMatch(code -> code.endsWith(actionPrefix + "delete"));
        Map<String, FieldAccessVO> mergedFieldAccess = new HashMap<>();

        for (Permission permission : permissions) {
            // 字段级配置仍由实体权限表提供，CRUD 统一以正式权限码为准。
            mergeFieldAccess(mergedFieldAccess, permission.getFieldAccess());
        }

        return new EntityAccessVO(read, create, update, delete, authorityCodes, mergedFieldAccess);
    }

    private void mergeFieldAccess(Map<String, FieldAccessVO> merged, String fieldAccessJson) {
        if (fieldAccessJson == null || fieldAccessJson.isBlank()) {
            return;
        }
        try {
            Map<String, FieldAccessVO> fields =
                    JsonUtils.parseObject(
                            fieldAccessJson, new TypeReference<Map<String, FieldAccessVO>>() {});
            for (var entry : fields.entrySet()) {
                merged.merge(
                        entry.getKey(),
                        entry.getValue(),
                        (existing, incoming) ->
                                new FieldAccessVO(
                                        existing.visible() || incoming.visible(),
                                        existing.editable() || incoming.editable()));
            }
        } catch (RuntimeException cause) {
            throw com.xuejiai.aaf.common.exception.ExceptionUtil.exception(
                    com.xuejiai.aaf.common.exception.GlobalErrorCode.FORBIDDEN);
        }
    }
}
