package com.xuejiai.aaf.framework.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 配置属性。
 *
 * @param secret 签名密钥
 * @param expireSeconds accessToken 有效期（秒），默认 2 小时
 * @param refreshExpireSeconds refreshToken 有效期（秒），默认 30 天
 * @param issuer 签发者
 * @param audience 受众
 */
@ConfigurationProperties(prefix = "aaf.security.jwt")
public record JwtProperties(
        String secret,
        long expireSeconds,
        long refreshExpireSeconds,
        String issuer,
        String audience) {

    public JwtProperties {
        if (secret == null) {
            secret = "aaf-default-secret-change-in-production";
        }
        if (expireSeconds <= 0) {
            expireSeconds = 7200L;
        }
        if (refreshExpireSeconds <= 0) {
            refreshExpireSeconds = 2592000L; // 30 天
        }
        if (issuer == null) {
            issuer = "xuejiai";
        }
        if (audience == null) {
            audience = "aaf-app";
        }
    }
}
