package com.xuejiai.aaf.framework.security.oauth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** OAuth 第三方登录配置属性。 */
@ConfigurationProperties(prefix = "aaf.security.oauth")
public record OAuthProperties(WechatConfig wechat, WecomConfig wecom, DingtalkConfig dingtalk) {

    public record WechatConfig(String appId, String appSecret, String redirectUri) {}

    public record WecomConfig(String corpId, String agentId, String secret, String redirectUri) {}

    public record DingtalkConfig(String clientId, String clientSecret, String redirectUri) {}
}
