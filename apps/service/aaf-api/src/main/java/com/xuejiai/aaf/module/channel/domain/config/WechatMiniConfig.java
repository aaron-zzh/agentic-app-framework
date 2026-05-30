package com.xuejiai.aaf.module.channel.domain.config;

/** 微信小程序平台配置。 */
public record WechatMiniConfig(
        String appId,
        String appSecret) implements PlatformConfig {}
