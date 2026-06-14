package com.xuejiai.aaf.framework.messaging.provider.dingtalk;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 钉钉自定义机器人配置。
 *
 * <p>apiKey 为群机器人的 access_token，secret 为加签密钥（可选）。
 * 可用于开发/测试环境替代短信/邮件接收验证码。
 */
@ConfigurationProperties(prefix = "aaf.messaging.dingtalk")
public record DingtalkProperties(
        /** 机器人 access_token */
        String apiKey,
        /** 加签密钥（可选，机器人配置了加签安全方式时填写） */
        String secret) {}
