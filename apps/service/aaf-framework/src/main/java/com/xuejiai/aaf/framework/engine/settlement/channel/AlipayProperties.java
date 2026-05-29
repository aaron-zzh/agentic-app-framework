package com.xuejiai.aaf.framework.engine.settlement.channel;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/** 支付宝配置属性 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "aaf.pay.alipay")
public class AlipayProperties {

    /** 是否启用支付宝 */
    private boolean enabled = false;

    /** 支付宝网关地址 */
    private String serverUrl = "https://openapi.alipay.com/gateway.do";

    /** 应用 ID */
    private String appId;

    /** 应用私钥（RSA2） */
    private String privateKey;

    /** 支付宝公钥 */
    private String alipayPublicKey;

    /** 异步通知地址 */
    private String notifyUrl;

    /** 同步跳转地址 */
    private String returnUrl;
}
