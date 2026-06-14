package com.xuejiai.aaf.framework.integration.wecom;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 企业微信配置（复用 aaf.security.oauth.wecom，避免重复配置）。
 *
 * <p>corpId + agentId + secret 覆盖所有企业应用能力：OAuth 登录、消息推送、OA、通讯录等。
 */
@ConfigurationProperties(prefix = "aaf.security.oauth.wecom")
public record WecomProperties(
        /** 企业 ID */
        String corpId,
        /** 应用 AgentId */
        String agentId,
        /** 应用 Secret */
        String secret,
        /** OAuth 回调地址（OAuth 登录用，消息推送不需要） */
        String redirectUri) {}
