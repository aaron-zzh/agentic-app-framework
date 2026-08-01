package com.xuejiai.aaf.module.system.role.service;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.framework.security.apikey.ApiKeyUserRoleProvider;
import com.xuejiai.aaf.module.system.role.domain.UserRole;
import com.xuejiai.aaf.module.system.role.repository.RoleRepository;
import com.xuejiai.aaf.module.system.role.repository.UserRoleRepository;

import lombok.RequiredArgsConstructor;

/**
 * M9：为 API Key 认证提供绑定用户的角色编码。
 *
 * <p>查询口径与登录签发 JWT 时（{@code AuthService#getRoleCodes}）完全一致——同样是 user_role → role.code，
 * 保证"API Key 调用"与"该用户自己登录"看到的角色一致，不额外放大也不额外收窄。
 */
@Component
@RequiredArgsConstructor
public class ApiKeyUserRoleAdapter implements ApiKeyUserRoleProvider {

    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;

    @Override
    @Transactional(readOnly = true)
    public List<String> roleCodesOf(Long userId) {
        if (userId == null) {
            return List.of();
        }
        var roleIds =
                userRoleRepository.findByUserIdAndDeletedFalse(userId).stream()
                        .map(UserRole::getRoleId)
                        .toList();
        if (roleIds.isEmpty()) {
            return List.of();
        }
        return roleRepository.findAllById(roleIds).stream().map(role -> role.getCode()).toList();
    }
}
