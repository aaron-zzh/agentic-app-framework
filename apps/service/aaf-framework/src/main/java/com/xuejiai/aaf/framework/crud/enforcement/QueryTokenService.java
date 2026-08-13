package com.xuejiai.aaf.framework.crud.enforcement;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.util.JsonUtils;

/** 查询窗口 HMAC token 服务；token 仅证明窗口上下文，不替代详情实时授权。 */
@Service
public final class QueryTokenService {

    private static final String VERSION = "v2";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String LIST_FIELD_SET = "list";

    private final byte[] secret;
    private final Duration ttl;
    private final Clock clock;

    @Autowired
    public QueryTokenService(
            @Value("${aaf.security.query-token.secret}") String secret,
            @Value("${aaf.security.query-token.ttl:PT5M}") Duration ttl) {
        this(secret, ttl, Clock.systemUTC());
    }

    QueryTokenService(String secret, Duration ttl, Clock clock) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("QueryToken HMAC 密钥至少需要 32 字节");
        }
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalStateException("QueryToken TTL 必须为正数");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.ttl = ttl;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public String issue(QueryTokenContext context, List<Long> ids) {
        requireContext(context);
        var issuedAt = Instant.now(clock);
        var claims =
                new QueryTokenClaims(
                        context.subjectId(),
                        context.orgId(),
                        context.workspaceId(),
                        context.resourceKey(),
                        context.fieldSet(),
                        context.queryHash(),
                        context.accessVersion(),
                        issuedAt.getEpochSecond(),
                        issuedAt.plus(ttl).getEpochSecond(),
                        List.copyOf(ids));
        var payload = encode(JsonUtils.toJsonString(claims).getBytes(StandardCharsets.UTF_8));
        return "%s.%s.%s".formatted(VERSION, payload, encode(sign(payload)));
    }

    public QueryTokenClaims validate(String token, QueryTokenContext expected, Long requiredId) {
        requireContext(expected);
        return validateToken(token, claims -> validateClaims(claims, expected, requiredId));
    }

    /**
     * 校验列表窗口 token 是否可用于详情读取。
     *
     * <p>仅允许 {@code list} 字段集签发的 token 切换到 {@code detail} 投影；签名、过期、主体、租户、资源、权限版本和窗口成员约束均保持不变。
     */
    public QueryTokenClaims validateForDetail(
            String token, DetailQueryTokenContext expected, Long requiredId) {
        requireDetailContext(expected);
        return validateToken(token, claims -> validateDetailClaims(claims, expected, requiredId));
    }

    private QueryTokenClaims validateToken(String token, TokenClaimsValidator validator) {
        try {
            var parts = token.split("\\.", 3);
            if (parts.length != 3 || !VERSION.equals(parts[0])) {
                throw new IllegalArgumentException("invalid token format");
            }
            var actualSignature = Base64.getUrlDecoder().decode(parts[2]);
            if (!MessageDigest.isEqual(sign(parts[1]), actualSignature)) {
                throw new IllegalArgumentException("invalid token signature");
            }
            var claims =
                    JsonUtils.parseObject(
                            new String(
                                    Base64.getUrlDecoder().decode(parts[1]),
                                    StandardCharsets.UTF_8),
                            QueryTokenClaims.class);
            validator.validate(claims);
            return claims;
        } catch (com.xuejiai.aaf.common.exception.BusinessException cause) {
            throw cause;
        } catch (RuntimeException cause) {
            throw exception(GlobalErrorCode.CRUD_QUERY_WINDOW_EXPIRED);
        }
    }

    private void validateClaims(
            QueryTokenClaims claims, QueryTokenContext expected, Long requiredId) {
        if (!hasExpectedBindings(
                claims,
                expected.subjectId(),
                expected.orgId(),
                expected.workspaceId(),
                expected.resourceKey(),
                expected.accessVersion())) {
            throw new IllegalArgumentException("stale token");
        }
        if (!Objects.equals(claims.fieldSet(), expected.fieldSet())) {
            throw new IllegalArgumentException("stale token");
        }
        requireWindowMember(claims, requiredId);
    }

    private void validateDetailClaims(
            QueryTokenClaims claims, DetailQueryTokenContext expected, Long requiredId) {
        if (!hasExpectedBindings(
                claims,
                expected.subjectId(),
                expected.orgId(),
                expected.workspaceId(),
                expected.resourceKey(),
                expected.accessVersion())) {
            throw new IllegalArgumentException("stale token");
        }
        if (!LIST_FIELD_SET.equals(claims.fieldSet())) {
            throw new IllegalArgumentException("stale token");
        }
        requireWindowMember(claims, requiredId);
    }

    private boolean hasExpectedBindings(
            QueryTokenClaims claims,
            Long subjectId,
            Long orgId,
            Long workspaceId,
            String resourceKey,
            String accessVersion) {
        var now = Instant.now(clock).getEpochSecond();
        return claims != null
                && claims.iat() <= now
                && claims.exp() > now
                && Objects.equals(claims.subjectId(), subjectId)
                && Objects.equals(claims.orgId(), orgId)
                && Objects.equals(claims.workspaceId(), workspaceId)
                && Objects.equals(claims.resourceKey(), resourceKey)
                && Objects.equals(claims.accessVersion(), accessVersion)
                && claims.queryHash() != null
                && !claims.queryHash().isBlank();
    }

    private void requireWindowMember(QueryTokenClaims claims, Long requiredId) {
        if (requiredId != null && !claims.ids().contains(requiredId)) {
            throw exception(
                    GlobalErrorCode.CRUD_RESOURCE_NOT_IN_QUERY_WINDOW, claims.resourceKey());
        }
    }

    private void requireContext(QueryTokenContext context) {
        if (context == null
                || context.subjectId() == null
                || context.resourceKey() == null
                || context.resourceKey().isBlank()
                || context.fieldSet() == null
                || context.fieldSet().isBlank()
                || context.queryHash() == null
                || context.queryHash().isBlank()
                || context.accessVersion() == null
                || context.accessVersion().isBlank()) {
            throw exception(GlobalErrorCode.FORBIDDEN);
        }
    }

    private void requireDetailContext(DetailQueryTokenContext context) {
        if (context == null
                || context.subjectId() == null
                || context.resourceKey() == null
                || context.resourceKey().isBlank()
                || context.accessVersion() == null
                || context.accessVersion().isBlank()) {
            throw exception(GlobalErrorCode.FORBIDDEN);
        }
    }

    private byte[] sign(String payload) {
        try {
            var mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("QueryToken HMAC 不可用", exception);
        }
    }

    private String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    @FunctionalInterface
    private interface TokenClaimsValidator {
        void validate(QueryTokenClaims claims);
    }

    public record QueryTokenContext(
            Long subjectId,
            Long orgId,
            Long workspaceId,
            String resourceKey,
            String fieldSet,
            String queryHash,
            String accessVersion) {}

    public record DetailQueryTokenContext(
            Long subjectId,
            Long orgId,
            Long workspaceId,
            String resourceKey,
            String accessVersion) {}

    public record QueryTokenClaims(
            Long subjectId,
            Long orgId,
            Long workspaceId,
            String resourceKey,
            String fieldSet,
            String queryHash,
            String accessVersion,
            long iat,
            long exp,
            List<Long> ids) {
        public QueryTokenClaims {
            ids = List.copyOf(ids);
        }
    }
}
