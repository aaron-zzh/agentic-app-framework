package com.xuejiai.aaf.framework.security;

import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.redis.core.StringRedisTemplate;
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

import com.xuejiai.aaf.framework.intelligent.assistant.AssistantAuthFilter;
import com.xuejiai.aaf.framework.security.access.PermissionVersionService;
import com.xuejiai.aaf.framework.security.apikey.ApiKeyAuthFilter;
import com.xuejiai.aaf.framework.security.apikey.ApiKeyScopeFilter;

/** Spring Security 配置，OAuth2 Resource Server + JWT + API Key。 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    /** 公开路径，无需认证 */
    private static final String[] PUBLIC_PATHS = {
        "/api/auth/**",
        "/api/public/**",
        "/api/channel/wx/**",
        "/api/system/chat/sessions/thread/*/messages",
        "/api/agui/run/**",
        "/api/hello",
        "/api/system/files/**",
        "/api/pay/orders/notify",
        "/api/pay/orders/*/redirect",
        "/api/pay/orders/by-merchant-order-no/*",
        "/swagger-ui/**",
        "/swagger-ui.html",
        "/v3/api-docs/**",
        "/error",
        "/ws/**"
    };

    @Bean
    public SecretKey jwtSecretKey(JwtProperties properties) {
        return new SecretKeySpec(properties.secret().getBytes(), "HmacSHA256");
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
            AssistantAuthFilter assistantAuthFilter,
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
                                auth.requestMatchers(PUBLIC_PATHS)
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
        http.addFilterAfter(assistantAuthFilter, ApiKeyAuthFilter.class);
        http.addFilterAfter(apiKeyScopeFilter, ApiKeyAuthFilter.class);
        return http.build();
    }
}
