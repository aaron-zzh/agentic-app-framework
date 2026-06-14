package com.xuejiai.aaf.module.channel.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.api.impl.WxMaServiceImpl;
import cn.binarywang.wx.miniapp.config.impl.WxMaDefaultConfigImpl;
import me.chanjar.weixin.mp.api.WxMpService;
import me.chanjar.weixin.mp.api.impl.WxMpServiceImpl;
import me.chanjar.weixin.mp.config.impl.WxMpDefaultConfigImpl;

/**
 * 微信渠道自动配置。
 *
 * <p>根据 aaf.channel.wx.mp.enabled / aaf.channel.wx.mini.enabled 按需创建 SDK Bean。 AccessToken
 * 当前使用内存存储；多实例部署时需接入 WxRedisOps 适配器（见 WxJava 文档）。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(WechatChannelProperties.class)
public class WechatChannelAutoConfiguration {

    @Bean
    @ConditionalOnProperty(name = "aaf.channel.wx.mp.enabled", havingValue = "true")
    public WxMpService wxMpService(WechatChannelProperties props) {
        var config = new WxMpDefaultConfigImpl();
        config.setAppId(props.mp().appId());
        config.setSecret(props.mp().secret());
        config.setToken(props.mp().token());
        config.setAesKey(props.mp().aesKey());
        var service = new WxMpServiceImpl();
        service.setWxMpConfigStorage(config);
        return service;
    }

    @Bean("wxMiniMpService")
    @ConditionalOnProperty(name = "aaf.channel.wx.mini.enabled", havingValue = "true")
    public WxMpService wxMiniMpService(WechatChannelProperties props) {
        var config = new WxMpDefaultConfigImpl();
        config.setAppId(props.mini().appId());
        config.setSecret(props.mini().secret());
        var service = new WxMpServiceImpl();
        service.setWxMpConfigStorage(config);
        return service;
    }

    @Bean
    @ConditionalOnProperty(name = "aaf.channel.wx.mini.enabled", havingValue = "true")
    public WxMaService wxMaService(WechatChannelProperties props) {
        var config = new WxMaDefaultConfigImpl();
        config.setAppid(props.mini().appId());
        config.setSecret(props.mini().secret());
        var service = new WxMaServiceImpl();
        service.setWxMaConfig(config);
        return service;
    }
}
