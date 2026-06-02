package com.xuejiai.aaf.framework.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import com.xuejiai.aaf.framework.security.access.PermissionVersionService;

import lombok.RequiredArgsConstructor;

/** 校验 JWT 中的权限版本，权限变更后要求客户端刷新认证上下文。 */
@RequiredArgsConstructor
public class JwtPermissionVersionValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error STALE_PERMISSION_VERSION =
            new OAuth2Error("stale_permission_version", "权限版本已过期，请刷新登录状态", null);

    private final PermissionVersionService permissionVersionService;

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        var tokenVersion = token.getClaimAsString("permissionVersion");
        if (tokenVersion == null || tokenVersion.isBlank()) {
            return OAuth2TokenValidatorResult.failure(STALE_PERMISSION_VERSION);
        }
        var currentVersion = permissionVersionService.permissionVersion();
        if (!tokenVersion.equals(currentVersion)) {
            return OAuth2TokenValidatorResult.failure(STALE_PERMISSION_VERSION);
        }
        return OAuth2TokenValidatorResult.success();
    }
}
