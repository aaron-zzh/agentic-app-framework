package com.xuejiai.aaf.module.channel.domain.config;

/** 企业微信平台配置。 */
public record WecomConfig(
        String corpId,
        String appSecret,
        String token,
        String encodingAesKey) implements PlatformConfig {}
