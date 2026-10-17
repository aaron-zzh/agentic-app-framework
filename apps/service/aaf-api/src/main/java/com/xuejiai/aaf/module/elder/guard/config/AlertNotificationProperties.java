package com.xuejiai.aaf.module.elder.guard.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 养老预警通知配置。 */
@ConfigurationProperties(prefix = "aaf.elder.alert")
public record AlertNotificationProperties(Sms sms) {

    public AlertNotificationProperties {
        sms = sms == null ? new Sms(false) : sms;
    }

    /** 短信通知开关；模板完成审核前保持关闭。 */
    public record Sms(boolean enabled) {}
}
