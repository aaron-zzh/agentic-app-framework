package com.xuejiai.aaf.module.device.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

import com.xuejiai.aaf.framework.security.LoggingAccessDeniedHandler;
import com.xuejiai.aaf.module.device.service.DeviceCredentialService;

import jakarta.servlet.http.HttpServletResponse;

/** 设备 API 独立安全链，不接受用户 JWT 或 API Key。 */
@Configuration
public class DeviceSecurityConfig {

    private static final String DEVICE_API = "/api/device/**";
    private static final String PAIR_START_API = "/api/device/pair/start";
    private static final String PAIR_POLL_API = "/api/device/pair/poll";

    @Bean
    @Order(-100)
    public SecurityFilterChain deviceSecurityFilterChain(
            HttpSecurity http,
            DeviceCredentialService credentialService,
            LoggingAccessDeniedHandler accessDeniedHandler,
            CorsConfigurationSource corsConfigurationSource)
            throws Exception {
        http.securityMatcher(DEVICE_API)
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(HttpMethod.POST, PAIR_START_API, PAIR_POLL_API)
                                        .permitAll()
                                        .anyRequest()
                                        .hasRole("DEVICE"))
                .exceptionHandling(
                        exceptions ->
                                exceptions
                                        .authenticationEntryPoint(
                                                (request, response, exception) -> {
                                                    response.setStatus(
                                                            HttpServletResponse.SC_UNAUTHORIZED);
                                                    response.setContentType(
                                                            "application/json;charset=UTF-8");
                                                    response.getWriter()
                                                            .write(
                                                                    "{\"code\":401,\"message\":\"Unauthorized\",\"data\":null}");
                                                })
                                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(
                        new DeviceAuthFilter(credentialService),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
