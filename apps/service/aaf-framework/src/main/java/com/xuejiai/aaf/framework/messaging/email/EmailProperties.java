package com.xuejiai.aaf.framework.messaging.email;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 邮件服务配置属性。 */
@ConfigurationProperties(prefix = "aaf.messaging.email")
public record EmailProperties(
        /** 发件人地址 */
        String from,
        /** 回复地址（可选） */
        String replyTo) {}
