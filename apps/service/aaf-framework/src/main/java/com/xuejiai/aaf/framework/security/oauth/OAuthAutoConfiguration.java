package com.xuejiai.aaf.framework.security.oauth;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/** OAuth 客户端自动配置，仅在对应配置非空时注册 Bean。 */
@Configuration
@EnableConfigurationProperties(OAuthProperties.class)
public class OAuthAutoConfiguration {

    private final RestClient restClient = RestClient.create();

    @Bean
    public WechatOAuthClient wechatOAuthClient(OAuthProperties properties) {
        if (properties.wechat() == null || !StringUtils.hasText(properties.wechat().appId())) {
            return null;
        }
        return new WechatOAuthClient(properties.wechat(), restClient);
    }

    @Bean
    public WecomOAuthClient wecomOAuthClient(OAuthProperties properties) {
        if (properties.wecom() == null || !StringUtils.hasText(properties.wecom().corpId())) {
            return null;
        }
        return new WecomOAuthClient(properties.wecom(), restClient);
    }

    @Bean
    public DingtalkOAuthClient dingtalkOAuthClient(OAuthProperties properties) {
        if (properties.dingtalk() == null || !StringUtils.hasText(properties.dingtalk().clientId())) {
            return null;
        }
        return new DingtalkOAuthClient(properties.dingtalk(), restClient);
    }
}
