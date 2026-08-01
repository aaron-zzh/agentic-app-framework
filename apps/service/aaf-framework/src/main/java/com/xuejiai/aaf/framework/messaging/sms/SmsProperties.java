package com.xuejiai.aaf.framework.messaging.sms;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 短信服务配置属性。 */
@ConfigurationProperties(prefix = "aaf.messaging.sms")
public record SmsProperties(
        /** 短信服务商：aliyun / tencent */
        String provider, AliyunConfig aliyun, TencentConfig tencent, TestSendConfig testSend) {

    public SmsProperties {
        if (testSend == null) {
            testSend = new TestSendConfig(true, List.of());
        }
    }

    public record AliyunConfig(String accessKeyId, String accessKeySecret, String signName) {}

    public record TencentConfig(String secretId, String secretKey, String appId, String signName) {}

    /**
     * M21：测试发送配置。
     *
     * <p>{@code enabled=false} 时整体禁用测试发送接口（生产环境应显式关闭）；{@code phoneWhitelist} 非空时限定
     * 仅白名单号码可被测试发送，避免误发真实短信到任意号码产生费用。两者可叠加：生产环境常见做法是保持 {@code enabled=true} 但配置固定的内部测试号码白名单。
     */
    public record TestSendConfig(Boolean enabled, List<String> phoneWhitelist) {
        public TestSendConfig {
            if (enabled == null) enabled = true;
            phoneWhitelist = phoneWhitelist == null ? List.of() : List.copyOf(phoneWhitelist);
        }
    }
}
