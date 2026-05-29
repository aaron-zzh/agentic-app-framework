package com.xuejiai.aaf.framework.engine.settlement.channel;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/** 微信支付配置属性 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "aaf.pay.wx")
public class WxPayProperties {

    /** 是否启用微信支付 */
    private boolean enabled = false;

    /** 微信应用 ID */
    private String appId;

    /** 商户号 */
    private String mchId;

    /** API V3 密钥 */
    private String apiV3Key;

    /** 商户私钥文件路径 */
    private String privateKeyPath;

    /** 商户证书文件路径 */
    private String privateCertPath;

    /** 支付回调通知地址 */
    private String notifyUrl;
}
