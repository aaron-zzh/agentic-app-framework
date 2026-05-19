package com.xuejiai.aaf.framework.messaging.sms;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.xuejiai.aaf.framework.messaging.ChannelSender;

/** 短信服务自动配置，根据 provider 条件注册对应实现。 */
@Configuration
@EnableConfigurationProperties(SmsProperties.class)
@ConditionalOnProperty(prefix = "aaf.messaging.sms", name = "provider")
public class SmsAutoConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "aaf.messaging.sms", name = "provider", havingValue = "aliyun")
    public SmsSender aliyunSmsSender(SmsProperties properties) {
        return new AliyunSmsSender(properties.aliyun());
    }

    @Bean
    @ConditionalOnProperty(prefix = "aaf.messaging.sms", name = "provider", havingValue = "tencent")
    public SmsSender tencentSmsSender(SmsProperties properties) {
        return new TencentSmsSender(properties.tencent());
    }

    @Bean
    @ConditionalOnProperty(prefix = "aaf.messaging.sms", name = "provider")
    public ChannelSender smsChannelSender(SmsSender smsSender, SmsRateLimiter rateLimiter) {
        return new SmsChannelSender(smsSender, rateLimiter);
    }
}
