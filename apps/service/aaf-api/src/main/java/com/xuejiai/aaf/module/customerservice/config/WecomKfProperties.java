package com.xuejiai.aaf.module.customerservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/** 企微客服配置属性 */
@Data
@ConfigurationProperties(prefix = "aaf.wecom.kf")
public class WecomKfProperties {

    /** 是否启用 */
    private boolean enabled = false;

    /** 企业ID */
    private String corpId;

    /** 应用密钥 */
    private String appSecret;

    /** 回调Token */
    private String token;

    /** 回调加密Key */
    private String encodingAesKey;

    /** Assistant 不可用时返回给客户的安全提示 */
    private String fallbackReply = "感谢您的咨询，我暂时无法回答这个问题，已为您转接人工客服。";
}
