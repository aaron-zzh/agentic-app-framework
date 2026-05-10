package com.xuejiai.aaf.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.CommonsRequestLoggingFilter;

/** 请求日志配置，需配合 logging.level 设为 DEBUG 生效。 */
@Configuration
public class RequestLoggingConfig {

    @Bean
    public CommonsRequestLoggingFilter requestLoggingFilter() {
        var filter = new CommonsRequestLoggingFilter();
        filter.setIncludeQueryString(true);
        filter.setIncludeHeaders(true);
        filter.setHeaderPredicate(name -> !"authorization".equalsIgnoreCase(name));
        filter.setIncludePayload(true);
        filter.setMaxPayloadLength(1000);
        return filter;
    }
}
