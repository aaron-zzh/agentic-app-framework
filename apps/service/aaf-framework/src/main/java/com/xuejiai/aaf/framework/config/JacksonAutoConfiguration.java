package com.xuejiai.aaf.framework.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import com.xuejiai.aaf.common.util.JsonUtils;

import tools.jackson.databind.json.JsonMapper;

/**
 * Jackson 配置——同时注册 Jackson 3 JsonMapper（新）和 Jackson 2 ObjectMapper（兼容旧注入点）。
 *
 * <p>TODO: v0.2 全量迁移到 Jackson 3 后移除 ObjectMapper Bean。
 */
@Configuration
public class JacksonAutoConfiguration {

    /** Jackson 3 JsonMapper——新代码使用 */
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

    /** Jackson 2 兼容 Bean——供未迁移的注入点使用，v0.2 迁移完后删除 */
    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        return mapper;
    }
}
