package com.xuejiai.aaf.framework.integration.wecom;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.xuejiai.aaf.framework.messaging.ChannelSender;
import com.xuejiai.aaf.framework.messaging.provider.wecom.WecomChannelSender;

import me.chanjar.weixin.cp.api.WxCpService;
import me.chanjar.weixin.cp.api.impl.WxCpServiceImpl;
import me.chanjar.weixin.cp.config.impl.WxCpDefaultConfigImpl;

/**
 * 企业微信自动配置。
 *
 * <p>配置 aaf.security.oauth.wecom.corp-id 后自动注册 WxCpService 和 WecomClient Bean。 与 OAuth 登录共用同一套
 * corpId/agentId/secret 配置。
 */
@Configuration
@EnableConfigurationProperties(WecomProperties.class)
@ConditionalOnClass(WxCpService.class)
@ConditionalOnProperty(
        prefix = "aaf.security.oauth.wecom",
        name = "corp-id",
        matchIfMissing = false)
public class WecomAutoConfiguration {

    @Bean
    public WxCpService wxCpService(WecomProperties props) {
        var config = new WxCpDefaultConfigImpl();
        config.setCorpId(props.corpId());
        if (props.agentId() != null) {
            config.setAgentId(Integer.parseInt(props.agentId()));
        }
        config.setCorpSecret(props.secret());
        var service = new WxCpServiceImpl();
        service.setWxCpConfigStorage(config);
        return service;
    }

    @Bean
    public WecomClient wecomClient(WxCpService wxCpService) {
        return new WecomClient(wxCpService);
    }

    @Bean
    public ChannelSender wecomChannelSender(WecomClient wecomClient) {
        return new WecomChannelSender(wecomClient);
    }
}
