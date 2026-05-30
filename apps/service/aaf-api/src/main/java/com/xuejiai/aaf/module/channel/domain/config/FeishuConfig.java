package com.xuejiai.aaf.module.channel.domain.config;

/** 飞书平台配置。 */
public record FeishuConfig(
        String appId,
        String appSecret,
        String verificationToken,
        String encryptKey) implements PlatformConfig {}
