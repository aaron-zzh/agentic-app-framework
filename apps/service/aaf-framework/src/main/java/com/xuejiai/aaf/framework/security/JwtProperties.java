package com.xuejiai.aaf.framework.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 配置属性。
 *
 * <p>AAF-022 实现时补充完整的签发/校验逻辑。
 *
 * @param secret 签名密钥
 * @param expireSeconds Token 有效期（秒）
 */
@ConfigurationProperties(prefix = "aaf.security.jwt")
public record JwtProperties(String secret, long expireSeconds) {

    public JwtProperties {
        if (secret == null) {
            secret = "aaf-default-secret-change-in-production";
        }
        if (expireSeconds <= 0) {
            expireSeconds = 7200L;
        }
    }
}
