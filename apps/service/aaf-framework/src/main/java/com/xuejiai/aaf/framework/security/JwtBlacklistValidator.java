package com.xuejiai.aaf.framework.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/** JWT 黑名单校验器，检查 token 是否已被登出。 */
public class JwtBlacklistValidator implements OAuth2TokenValidator<Jwt> {

    private final JwtUtils jwtUtils;

    public JwtBlacklistValidator(JwtUtils jwtUtils) {
        this.jwtUtils = jwtUtils;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        String jti = token.getId();
        if (jti != null && jwtUtils.isTokenBlacklisted(jti)) {
            return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "Token 已被撤销", null));
        }
        return OAuth2TokenValidatorResult.success();
    }
}
