package com.xuejiai.aaf.module.system.permission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import com.xuejiai.aaf.framework.security.cache.PermissionCacheService;
import com.xuejiai.aaf.module.system.permission.domain.PermissionCode;
import com.xuejiai.aaf.module.system.permission.repository.PermissionCodeRepository;
import com.xuejiai.aaf.module.system.role.domain.Role;
import com.xuejiai.aaf.module.system.role.domain.RolePermission;
import com.xuejiai.aaf.module.system.role.domain.UserRole;
import com.xuejiai.aaf.module.system.role.repository.RolePermissionRepository;
import com.xuejiai.aaf.module.system.role.repository.RoleRepository;
import com.xuejiai.aaf.module.system.role.repository.UserRoleRepository;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

class PermissionSecurityServiceTest extends BaseMockitoUnitTest {

    @Mock private PermissionCodeRepository permissionRepository;
    @Mock private UserRoleRepository userRoleRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private RolePermissionRepository rolePermissionRepository;
    @Mock private PermissionCacheService permissionCacheService;
    @InjectMocks private PermissionSecurityService service;

    @Test
    @DisplayName("Given 当前主体多个正式权限 When 查询 authority codes Then 返回全部启用码并回填同一鉴权缓存")
    void should_return_complete_enabled_authority_codes_from_formal_source() {
        // 准备参数
        var userRole = new UserRole();
        userRole.setRoleId(2L);
        var role = new Role();
        role.setId(2L);
        role.setCode("member");
        var firstRolePermission = new RolePermission();
        firstRolePermission.setPermissionId(11L);
        var secondRolePermission = new RolePermission();
        secondRolePermission.setPermissionId(12L);
        var action = permission(11L, "aigc:project:action", 0);
        var publish = permission(12L, "aigc:work:publish", 0);
        when(userRoleRepository.findByUserIdAndDeletedFalse(7L)).thenReturn(List.of(userRole));
        when(roleRepository.findAllById(List.of(2L))).thenReturn(List.of(role));
        when(permissionCacheService.getPermissions(7L))
                .thenReturn(null, Set.of("aigc:project:action", "aigc:work:publish"));
        when(rolePermissionRepository.findByRoleIdInAndDeletedFalse(List.of(2L)))
                .thenReturn(List.of(firstRolePermission, secondRolePermission));
        when(permissionRepository.findByIdInAndDeletedFalse(List.of(11L, 12L)))
                .thenReturn(List.of(action, publish));

        // 调用
        var authorityCodes = service.authorityCodes(7L);

        // 断言
        assertThat(authorityCodes).containsExactly("aigc:project:action", "aigc:work:publish");
        assertThat(service.hasPermission(7L, "aigc:project:action")).isTrue();
        verify(permissionCacheService)
                .putPermissions(7L, List.of("aigc:project:action", "aigc:work:publish"));
    }

    private PermissionCode permission(Long id, String code, int status) {
        var permission = new PermissionCode();
        permission.setId(id);
        permission.setCode(code);
        permission.setStatus(status);
        return permission;
    }
}
