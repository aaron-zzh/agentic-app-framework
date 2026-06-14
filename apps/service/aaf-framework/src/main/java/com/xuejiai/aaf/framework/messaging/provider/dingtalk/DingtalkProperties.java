package com.xuejiai.aaf.framework.messaging.provider.dingtalk;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 钉钉机器人配置。
 *
 * <p>支持自定义机器人 Webhook 消息推送，可用于开发/测试环境替代短信接收验证码。
 */
@ConfigurationProperties(prefix = "aaf.messaging.dingtalk")
public record DingtalkProperties(
        /** 机器人 Webhook URL（含 access_token） */
        String webhookUrl,
        /** 加签密钥（可选，配置后启用签名验证） */
        String secret) {}
