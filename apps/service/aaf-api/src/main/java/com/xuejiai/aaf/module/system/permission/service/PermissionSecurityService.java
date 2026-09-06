package com.xuejiai.aaf.module.system.permission.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.framework.security.authorization.FunctionPermissionChecker;
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
        var roleIds = roleIds(userId);
        if (roleIds.isEmpty()) {
            return false;
        }
        if (hasSuperAdmin(roleIds)) {
            return true;
        }
        return authorityCodes(userId, roleIds).contains(permissionCode.trim());
    }

    /** 返回当前主体完整正式权限码，供鉴权与前端 capability 投影共同使用。 */
    public Set<String> authorityCodes(Long userId) {
        if (userId == null) {
            return Set.of();
        }
        var roleIds = roleIds(userId);
        if (roleIds.isEmpty()) {
            return Set.of();
        }
        if (hasSuperAdmin(roleIds)) {
            return permissionRepository
                    .findByDeletedFalseOrderByModuleAscResourceAscActionAsc()
                    .stream()
                    .filter(permission -> STATUS_ENABLED == permission.getStatus())
                    .map(permission -> permission.getCode())
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        }
        return authorityCodes(userId, roleIds);
    }

    private Set<String> authorityCodes(Long userId, List<Long> roleIds) {
        var cachedPermissions = permissionCacheService.getPermissions(userId);
        if (cachedPermissions != null) {
            return cachedPermissions.stream()
                    .filter(code -> !"__EMPTY__".equals(code))
                    .sorted()
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        }
        var rolePermissionIds =
                rolePermissionRepository.findByRoleIdInAndDeletedFalse(roleIds).stream()
                        .map(RolePermission::getPermissionId)
                        .distinct()
                        .toList();
        var permissions =
                rolePermissionIds.isEmpty()
                        ? List.<String>of()
                        : permissionRepository.findByIdInAndDeletedFalse(rolePermissionIds).stream()
                                .filter(permission -> STATUS_ENABLED == permission.getStatus())
                                .map(permission -> permission.getCode())
                                .distinct()
                                .sorted()
                                .toList();
        permissionCacheService.putPermissions(userId, permissions);
        return new LinkedHashSet<>(permissions);
    }

    private List<Long> roleIds(Long userId) {
        return userRoleRepository.findByUserIdAndDeletedFalse(userId).stream()
                .map(UserRole::getRoleId)
                .distinct()
                .toList();
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
