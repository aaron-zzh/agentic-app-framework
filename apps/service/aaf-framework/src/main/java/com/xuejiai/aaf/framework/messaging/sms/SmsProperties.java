package com.xuejiai.aaf.framework.messaging.sms;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 短信服务配置属性。 */
@ConfigurationProperties(prefix = "aaf.messaging.sms")
public record SmsProperties(
        /** 短信服务商：aliyun / tencent */
        String provider, AliyunConfig aliyun, TencentConfig tencent) {

    public record AliyunConfig(String accessKeyId, String accessKeySecret, String signName) {}

    public record TencentConfig(String secretId, String secretKey, String appId, String signName) {}
}
