package com.xuejiai.aaf.module.customerservice.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/** 企微客服模块自动配置 */
@Configuration
@EnableConfigurationProperties(WecomKfProperties.class)
@ComponentScan("com.xuejiai.aaf.module.customerservice")
public class WecomKfAutoConfiguration {}
