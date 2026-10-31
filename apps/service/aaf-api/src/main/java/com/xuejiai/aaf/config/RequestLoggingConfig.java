package com.xuejiai.aaf.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.CommonsRequestLoggingFilter;

/**
 * 请求日志配置。只记录方法和路径，禁止记录可能包含 JWT、Cookie、密码或其他凭据的 query、header 与
 * payload。
 */
@Configuration
public class RequestLoggingConfig {

    @Bean
    public CommonsRequestLoggingFilter requestLoggingFilter() {
        var filter = new CommonsRequestLoggingFilter();
        filter.setIncludeQueryString(false);
        filter.setIncludeHeaders(false);
        filter.setIncludePayload(false);
        return filter;
    }
}
