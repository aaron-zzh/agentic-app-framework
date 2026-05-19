package com.xuejiai.aaf.framework.messaging.internal;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.xuejiai.aaf.framework.messaging.ChannelSender;

/** 站内信自动配置。 */
@Configuration
public class InternalMessageAutoConfiguration {

    @Bean
    @ConditionalOnBean(InternalMessageSender.class)
    public ChannelSender internalChannelSender(InternalMessageSender sender) {
        return new InternalChannelSender(sender);
    }
}
