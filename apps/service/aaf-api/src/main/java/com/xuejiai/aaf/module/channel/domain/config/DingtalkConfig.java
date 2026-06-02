package com.xuejiai.aaf.module.channel.domain.config;

/** 钉钉平台配置。 */
public record DingtalkConfig(String appKey, String appSecret, String webhookUrl, String secret)
        implements PlatformConfig {}
