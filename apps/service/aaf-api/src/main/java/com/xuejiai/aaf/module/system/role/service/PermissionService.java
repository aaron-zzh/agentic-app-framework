package com.xuejiai.aaf.module.system.role.service;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.xuejiai.aaf.module.system.auth.vo.FieldAccessVO;
import com.xuejiai.aaf.module.system.entity.vo.EntityAccessVO;
import com.xuejiai.aaf.module.system.role.domain.Permission;
import com.xuejiai.aaf.module.system.role.repository.PermissionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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
    private final ObjectMapper objectMapper;

    /** 计算用户对指定实体的权限：查询用户所有角色 → 合并角色下所有权限 → 返回 EntityAccess */
    public EntityAccessVO getEntityAccess(Long userId, String entitySlug) {
        var permissions = permissionRepository.findByUserIdAndEntitySlug(userId, entitySlug);

        boolean read = false;
        boolean create = false;
        boolean update = false;
        boolean delete = false;
        Map<String, FieldAccessVO> mergedFieldAccess = new HashMap<>();

        for (Permission p : permissions) {
            switch (p.getAction()) {
                case "read" -> read = true;
                case "create" -> create = true;
                case "update" -> update = true;
                case "delete" -> delete = true;
            }
            // 合并字段级权限（取并集，任一角色授权即有权）
            mergeFieldAccess(mergedFieldAccess, p.getFieldAccess());
        }

        return new EntityAccessVO(read, create, update, delete, mergedFieldAccess);
    }

    private void mergeFieldAccess(Map<String, FieldAccessVO> merged, String fieldAccessJson) {
        if (fieldAccessJson == null || fieldAccessJson.isBlank()) {
            return;
        }
        try {
            Map<String, FieldAccessVO> fields =
                    objectMapper.readValue(
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
        } catch (Exception e) {
            log.warn("解析 field_access JSON 失败: {}", e.getMessage());
        }
    }
}
