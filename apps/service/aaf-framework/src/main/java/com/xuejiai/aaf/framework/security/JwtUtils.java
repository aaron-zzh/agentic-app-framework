package com.xuejiai.aaf.framework.security;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

/** JWT 签发工具，基于 Spring Security 的 JwtEncoder（nimbus-jose）+ Redis 存储 refreshToken。 */
public class JwtUtils {

    private static final String REFRESH_TOKEN_PREFIX = "refresh_token:";
    private static final String TOKEN_BLACKLIST_PREFIX = "token_blacklist:";
    private static final String USER_SESSIONS_PREFIX = "user_sessions:";

    private final JwtEncoder jwtEncoder;
    private final StringRedisTemplate redisTemplate;
    private final long accessExpireSeconds;
    private final long refreshExpireSeconds;
    private final String issuer;
    private final String audience;

    public JwtUtils(
            JwtEncoder jwtEncoder,
            StringRedisTemplate redisTemplate,
            long accessExpireSeconds,
            long refreshExpireSeconds,
            String issuer,
            String audience) {
        this.jwtEncoder = jwtEncoder;
        this.redisTemplate = redisTemplate;
        this.accessExpireSeconds = accessExpireSeconds;
        this.refreshExpireSeconds = refreshExpireSeconds;
        this.issuer = issuer;
        this.audience = audience;
    }

    /** 签发 accessToken（含 jti） */
    public String generateToken(Long userId) {
        Instant now = Instant.now();
        JwtClaimsSet claims =
                JwtClaimsSet.builder()
                        .id(UUID.randomUUID().toString())
                        .subject(String.valueOf(userId))
                        .issuer(issuer)
                        .audience(java.util.List.of(audience))
                        .issuedAt(now)
                        .expiresAt(now.plusSeconds(accessExpireSeconds))
                        .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /** 签发 refreshToken 并存入 Redis */
    public String generateRefreshToken(Long userId) {
        String refreshToken = UUID.randomUUID().toString().replace("-", "");
        redisTemplate
                .opsForValue()
                .set(
                        REFRESH_TOKEN_PREFIX + refreshToken,
                        String.valueOf(userId),
                        Duration.ofSeconds(refreshExpireSeconds));
        return refreshToken;
    }

    /** 校验 refreshToken，返回 userId；无效返回 null */
    public Long validateRefreshToken(String refreshToken) {
        String userId = redisTemplate.opsForValue().get(REFRESH_TOKEN_PREFIX + refreshToken);
        return userId != null ? Long.valueOf(userId) : null;
    }

    /** 删除 refreshToken（登出） */
    public void revokeRefreshToken(String refreshToken) {
        redisTemplate.delete(REFRESH_TOKEN_PREFIX + refreshToken);
    }

    /** accessToken 过期时间点 */
    public LocalDateTime getAccessTokenExpiresTime() {
        return LocalDateTime.now().plusSeconds(accessExpireSeconds);
    }

    // ==================== Token 黑名单 ====================

    /** 将 accessToken 的 jti 加入黑名单 */
    public void blacklistToken(String jti, Instant expiresAt) {
        long ttl = Duration.between(Instant.now(), expiresAt).getSeconds();
        if (ttl > 0) {
            redisTemplate.opsForValue().set(TOKEN_BLACKLIST_PREFIX + jti, "1", Duration.ofSeconds(ttl));
        }
    }

    /** 检查 jti 是否在黑名单中 */
    public boolean isTokenBlacklisted(String jti) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(TOKEN_BLACKLIST_PREFIX + jti));
    }

    // ==================== 多端会话管理 ====================

    /** 记录用户设备会话 */
    public void saveSession(Long userId, String deviceId, String refreshToken) {
        String key = USER_SESSIONS_PREFIX + userId;
        redisTemplate.opsForHash().put(key, deviceId, refreshToken + "|" + Instant.now().toString());
        redisTemplate.expire(key, Duration.ofSeconds(refreshExpireSeconds));
    }

    /** 移除指定设备会话 */
    public void removeSession(Long userId, String deviceId) {
        String key = USER_SESSIONS_PREFIX + userId;
        String value = (String) redisTemplate.opsForHash().get(key, deviceId);
        if (value != null) {
            String refreshToken = value.split("\\|")[0];
            revokeRefreshToken(refreshToken);
        }
        redisTemplate.opsForHash().delete(key, deviceId);
    }

    /** 获取用户所有活跃会话 */
    public Map<Object, Object> getSessions(Long userId) {
        return redisTemplate.opsForHash().entries(USER_SESSIONS_PREFIX + userId);
    }
}
