package com.xuejiai.aaf.framework.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.xuejiai.aaf.common.util.JsonUtils;

import tools.jackson.databind.json.JsonMapper;

/** Jackson 配置——注册 Spring 管理的 JsonMapper Bean，供 JsonUtils 复用统一序列化配置。 */
@Configuration
public class JacksonAutoConfiguration {

    @Bean
    @Primary
    @ConditionalOnMissingBean(JsonMapper.class)
    public JsonMapper jsonMapper() {
        JsonMapper mapper =
                JsonMapper.builder()
                        .disable(
                                tools.jackson.databind.DeserializationFeature
                                        .FAIL_ON_UNKNOWN_PROPERTIES)
                        .build();
        JsonUtils.init(mapper);
        return mapper;
    }
}
