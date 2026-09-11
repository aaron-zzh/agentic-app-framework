package com.xuejiai.aaf.module.ai.chat.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.security.JwtProperties;

/** 签发并严格校验匿名客服访客凭证。 */
@Service
public class CustomerServiceVisitorTokenService {

    public static final String COOKIE_NAME = "aaf-customer-service";
    public static final String COOKIE_PATH = "/api/public/customer-service";

    private static final String PURPOSE = "customer-service-visitor";

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final JwtProperties properties;
    private final Environment environment;
    private final String issuer;
    private final String audience;

    public CustomerServiceVisitorTokenService(
            JwtEncoder encoder,
            SecretKey secretKey,
            JwtProperties properties,
            Environment environment) {
        this.encoder = encoder;
        this.properties = properties;
        this.environment = environment;
        this.issuer = properties.issuer() + "/customer-service";
        this.audience = properties.audience() + "/customer-service";

        var strictDecoder = NimbusJwtDecoder.withSecretKey(secretKey).build();
        strictDecoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(
                        JwtValidators.createDefaultWithIssuer(issuer),
                        new JwtClaimValidator<List<String>>(
                                "aud",
                                values -> values != null && values.equals(List.of(audience))),
                        new JwtClaimValidator<String>("purpose", PURPOSE::equals)));
        this.decoder = strictDecoder;
    }

    public VisitorToken issueNew() {
        return issue(UUID.randomUUID().toString());
    }

    public VisitorToken renew(String subject) {
        return issue(requireUuid(subject));
    }

    public String verify(String token) {
        if (token == null || token.isBlank()) {
            throw unauthorized();
        }
        try {
            Jwt jwt = decoder.decode(token);
            if (jwt.getIssuedAt() == null
                    || jwt.getExpiresAt() == null
                    || !PURPOSE.equals(jwt.getClaimAsString("purpose"))) {
                throw unauthorized();
            }
            return requireUuid(jwt.getSubject());
        } catch (JwtException | IllegalArgumentException failure) {
            throw unauthorized();
        }
    }

    public ResponseCookie cookie(String token) {
        var production = environment.acceptsProfiles(Profiles.of("prod"));
        return ResponseCookie.from(COOKIE_NAME, token)
                .httpOnly(true)
                .secure(production)
                .sameSite(production ? "Strict" : "Lax")
                .path(COOKIE_PATH)
                .maxAge(properties.refreshExpireSeconds())
                .build();
    }

    private VisitorToken issue(String subject) {
        var now = Instant.now();
        var claims =
                JwtClaimsSet.builder()
                        .id(UUID.randomUUID().toString())
                        .issuer(issuer)
                        .audience(List.of(audience))
                        .subject(subject)
                        .claim("purpose", PURPOSE)
                        .issuedAt(now)
                        .expiresAt(now.plusSeconds(properties.refreshExpireSeconds()))
                        .build();
        var header = JwsHeader.with(MacAlgorithm.HS256).build();
        var token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new VisitorToken(subject, token);
    }

    private static String requireUuid(String subject) {
        if (subject == null || subject.length() > 64) {
            throw unauthorized();
        }
        try {
            var value = UUID.fromString(subject).toString();
            if (!value.equals(subject)) {
                throw unauthorized();
            }
            return value;
        } catch (IllegalArgumentException failure) {
            throw unauthorized();
        }
    }

    private static BusinessException unauthorized() {
        return new BusinessException(GlobalErrorCode.UNAUTHORIZED, "访客凭证无效");
    }

    public record VisitorToken(String subject, String token) {}
}
