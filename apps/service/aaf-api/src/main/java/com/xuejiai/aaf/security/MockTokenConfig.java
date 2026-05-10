package com.xuejiai.aaf.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 模拟 Token 配置，开发调试用。
 *
 * <p>开启：aaf.security.mock-enable=true（仅 dev 环境配置）。 使用：Authorization: Bearer test{userId}，如 Bearer
 * test1 表示用户 1。
 */
@Configuration
@ConditionalOnProperty(name = "aaf.security.mock-enable", havingValue = "true")
public class MockTokenConfig {

    private static final String MOCK_SECRET = "test";

    @Bean
    @Order(0)
    public SecurityFilterChain mockSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher(request -> {
                    String auth = request.getHeader("Authorization");
                    return auth != null && auth.startsWith("Bearer " + MOCK_SECRET);
                })
                .csrf(csrf -> csrf.disable())
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(
                        new MockTokenFilter(MOCK_SECRET),
                        BearerTokenAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated());
        return http.build();
    }
}
