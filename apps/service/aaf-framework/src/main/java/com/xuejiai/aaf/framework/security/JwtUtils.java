package com.xuejiai.aaf.framework.security;

import java.time.Instant;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

/** JWT 签发工具，基于 Spring Security 的 JwtEncoder（nimbus-jose）。 */
public class JwtUtils {

    private final JwtEncoder jwtEncoder;
    private final long expireSeconds;

    public JwtUtils(JwtEncoder jwtEncoder, long expireSeconds) {
        this.jwtEncoder = jwtEncoder;
        this.expireSeconds = expireSeconds;
    }

    /** 签发 Token */
    public String generateToken(Long userId) {
        Instant now = Instant.now();
        JwtClaimsSet claims =
                JwtClaimsSet.builder()
                        .subject(String.valueOf(userId))
                        .issuedAt(now)
                        .expiresAt(now.plusSeconds(expireSeconds))
                        .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
