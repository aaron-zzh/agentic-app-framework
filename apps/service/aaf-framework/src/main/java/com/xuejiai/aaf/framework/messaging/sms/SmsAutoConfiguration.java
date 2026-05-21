package com.xuejiai.aaf.framework.messaging.sms;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.xuejiai.aaf.framework.messaging.ChannelSender;

/** 短信服务自动配置，支持多厂商动态路由。 */
@Configuration
@EnableConfigurationProperties(SmsProperties.class)
@ConditionalOnProperty(prefix = "aaf.messaging.sms", name = "provider")
public class SmsAutoConfiguration {

    @Bean
    public SmsSenderRouter smsSenderRouter(SmsProperties properties) {
        Map<String, SmsSender> senders = new HashMap<>();
        if (properties.aliyun() != null
                && properties.aliyun().accessKeyId() != null
                && !properties.aliyun().accessKeyId().isBlank()) {
            senders.put("aliyun", new AliyunSmsSender(properties.aliyun()));
        }
        if (properties.tencent() != null
                && properties.tencent().secretId() != null
                && !properties.tencent().secretId().isBlank()) {
            senders.put("tencent", new TencentSmsSender(properties.tencent()));
        }
        if (senders.isEmpty()) {
            throw new IllegalStateException("未配置任何短信厂商，请检查 aaf.messaging.sms 配置");
        }
        return new SmsSenderRouter(senders, properties.provider());
    }

    @Bean
    public SmsSender smsSender(SmsSenderRouter router) {
        return router;
    }

    @Bean
    public ChannelSender smsChannelSender(
            SmsSenderRouter router,
            SmsRateLimiter rateLimiter,
            ApplicationEventPublisher eventPublisher,
            SmsProperties properties) {
        return new SmsChannelSender(router, rateLimiter, eventPublisher, properties.provider());
    }
}
