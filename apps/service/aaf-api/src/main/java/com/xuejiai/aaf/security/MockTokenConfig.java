package com.xuejiai.aaf.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 模拟 Token 配置，开发调试用。
 *
 * <p>开启：aaf.security.mock-enable=true（仅 dev 环境配置）。 使用：Authorization: Bearer test{userId}，如 Bearer
 * test1 表示用户 1。
 *
 * <p>B-mock 生产硬隔离（双保险）：{@code Bearer test{userId}} 等价于全量身份伪造，只靠配置开关关闭风险过高。
 *
 * <ul>
 *   <li>{@link ConditionalOnProperty}：仅 {@code aaf.security.mock-enable=true} 时装配
 *   <li>{@link Profile}：{@code prod} Profile 下即使配置误开也不装配该过滤器链
 * </ul>
 *
 * <p>与 {@code MockPayChannelAdapter} 采用同一隔离模式，保持一致。
 */
@Configuration
@Profile("!prod")
@ConditionalOnProperty(name = "aaf.security.mock-enable", havingValue = "true")
public class MockTokenConfig {

    private static final String MOCK_SECRET = "test";

    @Bean
    @Order(0)
    public SecurityFilterChain mockSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher(
                        request -> {
                            String auth = request.getHeader("Authorization");
                            return auth != null && auth.startsWith("Bearer " + MOCK_SECRET);
                        })
                .csrf(csrf -> csrf.disable())
                .cors(cors -> {})
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(
                        new MockTokenFilter(MOCK_SECRET),
                        UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated());
        return http.build();
    }
}
