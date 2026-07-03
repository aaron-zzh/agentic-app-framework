package com.xuejiai.aaf.module.system.permission.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.framework.security.access.FunctionPermissionChecker;
import com.xuejiai.aaf.framework.security.cache.PermissionCacheService;
import com.xuejiai.aaf.module.system.permission.repository.PermissionCodeRepository;
import com.xuejiai.aaf.module.system.role.domain.Role;
import com.xuejiai.aaf.module.system.role.domain.RolePermission;
import com.xuejiai.aaf.module.system.role.domain.UserRole;
import com.xuejiai.aaf.module.system.role.repository.RolePermissionRepository;
import com.xuejiai.aaf.module.system.role.repository.RoleRepository;
import com.xuejiai.aaf.module.system.role.repository.UserRoleRepository;

import lombok.RequiredArgsConstructor;

/** 功能权限码检查服务，作为 Spring Security hasPermission 的业务侧实现。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PermissionSecurityService implements FunctionPermissionChecker {

    private static final int STATUS_ENABLED = 0;

    private final PermissionCodeRepository permissionRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final PermissionCacheService permissionCacheService;

    @Override
    public boolean hasPermission(Long userId, String permissionCode) {
        if (userId == null || permissionCode == null || permissionCode.isBlank()) {
            return false;
        }

        var userRoles = userRoleRepository.findByUserIdAndDeletedFalse(userId);
        var roleIds = userRoles.stream().map(UserRole::getRoleId).toList();
        if (roleIds.isEmpty()) {
            return false;
        }
        if (hasSuperAdmin(roleIds)) {
            return true;
        }

        var cachedPermissions = permissionCacheService.getPermissions(userId);
        if (cachedPermissions != null) {
            return cachedPermissions.contains(permissionCode.trim());
        }

        var rolePermissionIds =
                rolePermissionRepository.findByRoleIdInAndDeletedFalse(roleIds).stream()
                        .map(RolePermission::getPermissionId)
                        .toList();
        if (rolePermissionIds.isEmpty()) {
            permissionCacheService.putPermissions(userId, List.of());
            return false;
        }
        var permissions =
                permissionRepository.findByIdInAndDeletedFalse(rolePermissionIds).stream()
                        .filter(permission -> STATUS_ENABLED == permission.getStatus())
                        .map(permission -> permission.getCode())
                        .toList();
        permissionCacheService.putPermissions(userId, permissions);
        return permissions.contains(permissionCode.trim());
    }

    @Override
    public boolean isRegistered(String permissionCode) {
        if (permissionCode == null || permissionCode.isBlank()) {
            return false;
        }
        return permissionRepository.existsByCodeAndDeletedFalse(permissionCode.trim());
    }

    private boolean hasSuperAdmin(List<Long> roleIds) {
        return roleRepository.findAllById(roleIds).stream()
                .map(Role::getCode)
                .anyMatch(
                        code -> "SUPER_ADMIN".equalsIgnoreCase(code) || "super_admin".equals(code));
    }
}
