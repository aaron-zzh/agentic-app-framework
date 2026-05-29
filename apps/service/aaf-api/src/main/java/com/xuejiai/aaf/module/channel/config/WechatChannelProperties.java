package com.xuejiai.aaf.module.channel.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 微信渠道配置属性。 */
@ConfigurationProperties(prefix = "aaf.channel.wx")
public record WechatChannelProperties(MpConfig mp, MiniConfig mini) {

    public record MpConfig(boolean enabled, String appId, String secret, String token, String aesKey) {
        public MpConfig {
            if (appId == null) appId = "";
            if (secret == null) secret = "";
            if (token == null) token = "";
            if (aesKey == null) aesKey = "";
        }
    }

    public record MiniConfig(boolean enabled, String appId, String secret) {
        public MiniConfig {
            if (appId == null) appId = "";
            if (secret == null) secret = "";
        }
    }

    public WechatChannelProperties {
        if (mp == null) mp = new MpConfig(false, "", "", "", "");
        if (mini == null) mini = new MiniConfig(false, "", "");
    }
}
