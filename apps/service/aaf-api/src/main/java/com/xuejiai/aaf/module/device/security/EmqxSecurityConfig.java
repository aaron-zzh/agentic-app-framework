package com.xuejiai.aaf.module.device.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import com.xuejiai.aaf.module.device.mqtt.MqttConnectionConfig;

/** EMQX 内部回调独立安全链。请求身份由控制器中的共享密钥做常数时间校验。 */
@Configuration
@EnableConfigurationProperties(MqttConnectionConfig.class)
public class EmqxSecurityConfig {

    @Bean
    @Order(-110)
    public SecurityFilterChain emqxSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/api/internal/emqx/**")
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.disable())
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
