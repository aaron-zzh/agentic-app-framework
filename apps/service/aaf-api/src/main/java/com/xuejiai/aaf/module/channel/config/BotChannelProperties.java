package com.xuejiai.aaf.module.channel.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 钉钉/飞书机器人渠道配置属性。 */
@ConfigurationProperties(prefix = "aaf.channel")
public record BotChannelProperties(DingtalkProperties dingtalk, FeishuProperties feishu) {

    public BotChannelProperties {
        if (dingtalk == null) dingtalk = new DingtalkProperties(null, null, null, null);
        if (feishu == null) feishu = new FeishuProperties(null, null, null, null);
    }

    public record DingtalkProperties(
            /** 机器人 Webhook URL */
            String webhookUrl,
            /** 加签密钥 */
            String secret,
            /** 应用 AppKey（用于主动发消息） */
            String appKey,
            /** 应用 AppSecret */
            String appSecret) {}

    public record FeishuProperties(
            /** 应用 App ID */
            String appId,
            /** 应用 App Secret */
            String appSecret,
            /** 事件订阅 Verification Token */
            String verificationToken,
            /** 事件订阅 Encrypt Key */
            String encryptKey) {}
}
