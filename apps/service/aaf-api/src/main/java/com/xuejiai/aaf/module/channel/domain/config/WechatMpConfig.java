package com.xuejiai.aaf.module.channel.domain.config;

/** 微信公众号平台配置。 */
public record WechatMpConfig(String appId, String appSecret, String token, String aesKey)
        implements PlatformConfig {}
