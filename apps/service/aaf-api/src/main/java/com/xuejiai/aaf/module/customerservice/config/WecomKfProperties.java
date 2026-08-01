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

    /**
     * m9：回调异步处理并发上限（同时在跑的 handleCallback 数量）。
     *
     * <p>超出上限的回调直接丢弃（记录日志），不阻塞排队——企微服务器对回调有自己的重试机制，
     * 阻塞等待会拖慢本次 HTTP 响应，可能被企微判定超时后重发，形成雪崩；直接丢弃让企微按其重试节奏重新推送更安全。
     */
    private int maxConcurrentCallbacks = 200;
}
