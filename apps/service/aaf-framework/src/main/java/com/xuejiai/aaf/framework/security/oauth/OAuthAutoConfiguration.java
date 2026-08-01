package com.xuejiai.aaf.framework.security.oauth;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/** OAuth 客户端自动配置，仅在对应配置非空时注册 Bean。 */
@Configuration
@EnableConfigurationProperties(OAuthProperties.class)
public class OAuthAutoConfiguration {

    private final RestClient restClient = RestClient.create();

    @Bean
    @Conditional(WechatConfiguredCondition.class)
    public WechatOAuthClient wechatOAuthClient(OAuthProperties properties) {
        return new WechatOAuthClient(properties.wechat(), restClient);
    }

    @Bean
    @Conditional(WecomConfiguredCondition.class)
    public WecomOAuthClient wecomOAuthClient(OAuthProperties properties) {
        return new WecomOAuthClient(properties.wecom(), restClient);
    }

    @Bean
    @Conditional(DingtalkConfiguredCondition.class)
    public DingtalkOAuthClient dingtalkOAuthClient(OAuthProperties properties) {
        return new DingtalkOAuthClient(properties.dingtalk(), restClient);
    }

    static final class WechatConfiguredCondition implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return StringUtils.hasText(
                    context.getEnvironment().getProperty("aaf.security.oauth.wechat.app-id"));
        }
    }

    static final class WecomConfiguredCondition implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return StringUtils.hasText(
                    context.getEnvironment().getProperty("aaf.security.oauth.wecom.corp-id"));
        }
    }

    static final class DingtalkConfiguredCondition implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return StringUtils.hasText(
                    context.getEnvironment().getProperty("aaf.security.oauth.dingtalk.client-id"));
        }
    }
}
