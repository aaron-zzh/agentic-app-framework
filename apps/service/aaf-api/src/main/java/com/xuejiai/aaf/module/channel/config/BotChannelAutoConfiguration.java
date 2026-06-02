package com.xuejiai.aaf.module.channel.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 钉钉/飞书机器人渠道自动配置。
 *
 * <p>仅加载配置属性，实际 adapter Bean 通过各自的 @ConditionalOnProperty 控制注册。
 */
@Configuration
@EnableConfigurationProperties(BotChannelProperties.class)
public class BotChannelAutoConfiguration {}
