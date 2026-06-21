package com.xuejiai.aaf.framework.messaging.provider.dingtalk;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import com.xuejiai.aaf.framework.messaging.ChannelSender;

import tools.jackson.databind.ObjectMapper;

/**
 * 钉钉渠道自动配置。
 *
 * <p>配置 aaf.messaging.dingtalk.webhook-url 后自动注册。
 */
@Configuration
@EnableConfigurationProperties(DingtalkProperties.class)
@ConditionalOnProperty(prefix = "aaf.messaging.dingtalk", name = "api-key")
public class DingtalkAutoConfiguration {

    @Bean
    public ChannelSender dingtalkChannelSender(
            DingtalkProperties properties,
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper) {
        return new DingtalkChannelSender(properties, restClientBuilder.build(), objectMapper);
    }
}
