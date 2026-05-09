package com.xuejiai.aaf.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 模拟 Token 配置，开发调试用。
 *
 * <p>开启：aaf.security.mock-enable=true（仅 dev 环境配置）。 使用：Authorization: Bearer test{userId}，如 Bearer
 * test1 表示用户 1。
 */
@Configuration
@ConditionalOnProperty(name = "aaf.security.mock-enable", havingValue = "true")
public class MockTokenConfig {

    @Bean
    public FilterRegistrationBean<MockTokenFilter> mockTokenFilter() {
        var registration = new FilterRegistrationBean<>(new MockTokenFilter("test"));
        registration.setOrder(-100);
        registration.addUrlPatterns("/api/*");
        return registration;
    }
}
