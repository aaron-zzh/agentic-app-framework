package com.xuejiai.aaf.framework.security;

import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import com.xuejiai.aaf.framework.security.apikey.ApiKeyAuthFilter;
import com.xuejiai.aaf.framework.security.apikey.ApiKeyScopeFilter;
import com.xuejiai.aaf.framework.security.authorization.PermissionVersionService;

/** Spring Security 配置，OAuth2 Resource Server + JWT + API Key。 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    /** 产品级公开命名空间和基础设施端点。特殊协议入口按 HTTP 方法单独声明。 */
    static final String[] PRODUCT_PUBLIC_PATHS = {
        "/api/public/**",
        "/api/hello",
        "/swagger-ui/**",
        "/swagger-ui.html",
        "/v3/api-docs/**",
        "/error",
        "/ws/**"
    };

    static final String[] PUBLIC_POST_PATHS = {
        "/api/auth/login",
        "/api/auth/register",
        "/api/auth/register-by-email",
        "/api/auth/verify-email",
        "/api/auth/send-email-code",
        "/api/auth/send-sms-code",
        "/api/auth/login-by-email",
        "/api/auth/login-by-phone",
        "/api/auth/reset-password",
        "/api/auth/reset-password-by-phone",
        "/api/auth/refresh",
        "/api/auth/logout",
        "/api/auth/oauth/*/callback",
        "/api/auth/oauth/exchange",
        "/api/system/auth/captcha/verify",
        "/api/wecom/kf/callback",
        "/api/channel/wx/mp/callback",
        "/api/channel/wx/mini/callback",
        "/api/channel/wx/mini/login",
        "/api/channel/wx/mini/phone-login",
        "/api/channel/feishu/callback",
        "/api/channel/webhook/inbound",
        "/api/pay/orders/notify/wx",
        "/api/pay/orders/notify/alipay"
    };

    static final String[] PUBLIC_GET_PATHS = {
        "/api/auth/oauth/*/url",
        "/api/auth/oauth/*/redirect",
        "/api/system/auth/captcha",
        "/api/wecom/kf/callback",
        "/api/channel/wx/mp/callback"
    };

    @Bean
    public SecretKey jwtSecretKey(JwtProperties properties) {
        var secret = properties.secret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("JWT HS256 密钥至少需要 32 字节");
        }
        return new SecretKeySpec(secret, "HmacSHA256");
    }

    @Bean
    public JwtDecoder jwtDecoder(
            SecretKey jwtSecretKey,
            JwtProperties properties,
            JwtUtils jwtUtils,
            PermissionVersionService permissionVersionService) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey).build();
        // 验证 issuer、audience 和黑名单
        OAuth2TokenValidator<org.springframework.security.oauth2.jwt.Jwt> validators =
                new DelegatingOAuth2TokenValidator<>(
                        JwtValidators.createDefaultWithIssuer(properties.issuer()),
                        new JwtClaimValidator<java.util.List<String>>(
                                "aud", aud -> aud != null && aud.contains(properties.audience())),
                        new JwtBlacklistValidator(jwtUtils),
                        new JwtPermissionVersionValidator(permissionVersionService));
        decoder.setJwtValidator(validators);
        return decoder;
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
    }

    @Bean
    public JwtUtils jwtUtils(
            JwtEncoder jwtEncoder,
            StringRedisTemplate redisTemplate,
            JwtProperties properties,
            PermissionVersionService permissionVersionService) {
        return new JwtUtils(
                jwtEncoder,
                redisTemplate,
                properties.expireSeconds(),
                properties.refreshExpireSeconds(),
                properties.issuer(),
                properties.audience(),
                permissionVersionService);
    }

    @Bean
    public RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.fromHierarchy(
                """
                ROLE_SUPER_ADMIN > ROLE_ADMIN
                ROLE_ADMIN > ROLE_MEMBER
                ROLE_MEMBER > ROLE_GUEST
                """);
    }

    @Bean
    public MethodSecurityExpressionHandler methodSecurityExpressionHandler(
            PermissionEvaluator permissionEvaluator, RoleHierarchy roleHierarchy) {
        var handler = new DefaultMethodSecurityExpressionHandler();
        handler.setPermissionEvaluator(permissionEvaluator);
        handler.setRoleHierarchy(roleHierarchy);
        return handler;
    }

    @Bean
    public Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter() {
        return jwt -> {
            List<String> roles = jwt.getClaimAsStringList("roles");
            var authorities =
                    (roles == null ? List.<String>of() : roles)
                            .stream()
                                    .map(this::toRoleAuthority)
                                    .distinct()
                                    .map(SimpleGrantedAuthority::new)
                                    .toList();
            return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
        };
    }

    private String toRoleAuthority(String role) {
        var normalized = role == null ? "" : role.trim().toUpperCase().replace('-', '_');
        if ("ORG_ADMIN".equals(normalized)) {
            normalized = "ADMIN";
        }
        return normalized.startsWith("ROLE_") ? normalized : "ROLE_" + normalized;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ApiKeyAuthFilter apiKeyAuthFilter,
            ApiKeyScopeFilter apiKeyScopeFilter,
            SseTokenFilter sseTokenFilter,
            Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter,
            LoggingAccessDeniedHandler accessDeniedHandler,
            org.springframework.web.cors.CorsConfigurationSource corsConfigurationSource)
            throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(PRODUCT_PUBLIC_PATHS)
                                        .permitAll()
                                        .requestMatchers(HttpMethod.POST, PUBLIC_POST_PATHS)
                                        .permitAll()
                                        .requestMatchers(HttpMethod.GET, PUBLIC_GET_PATHS)
                                        .permitAll()
                                        // 健康探针保持公开，供容器/编排做存活与就绪检查
                                        .requestMatchers("/actuator/health/**")
                                        .permitAll()
                                        // 其余 actuator 端点（info/prometheus/loggers 等）暴露
                                        // 内存、流量、日志级别等敏感信息，必须管理员认证后访问
                                        .requestMatchers("/actuator/**")
                                        .hasRole("ADMIN")
                                        .anyRequest()
                                        .authenticated())
                .exceptionHandling(ex -> ex.accessDeniedHandler(accessDeniedHandler))
                .oauth2ResourceServer(
                        oauth2 ->
                                oauth2.jwt(
                                        jwt ->
                                                jwt.jwtAuthenticationConverter(
                                                        jwtAuthenticationConverter)))
                .addFilterBefore(
                        sseTokenFilter,
                        org.springframework.security.oauth2.server.resource.web.authentication
                                .BearerTokenAuthenticationFilter.class)
                .addFilterBefore(
                        apiKeyAuthFilter,
                        org.springframework.security.web.authentication
                                .UsernamePasswordAuthenticationFilter.class);
        http.addFilterAfter(apiKeyScopeFilter, ApiKeyAuthFilter.class);
        return http.build();
    }
}
