package com.xuejiai.aaf.module.channel.vo;

import java.time.LocalDateTime;

import com.xuejiai.aaf.module.channel.domain.WebhookConfig;

/**
 * Webhook 配置视图对象。
 *
 * <p>HMAC 密钥不出参，仅以布尔标记表示"是否已配置"。
 */
public record WebhookConfigVO(
        Long id,
        String name,
        String url,
        String eventTypes,
        boolean secretConfigured,
        String status,
        String direction,
        Integer failureCount,
        Integer maxRetries,
        LocalDateTime createTime,
        LocalDateTime updateTime) {

    public static WebhookConfigVO from(WebhookConfig entity) {
        return new WebhookConfigVO(
                entity.getId(),
                entity.getName(),
                entity.getUrl(),
                entity.getEventTypes(),
                entity.getSecret() != null && !entity.getSecret().isBlank(),
                entity.getStatus(),
                entity.getDirection(),
                entity.getFailureCount(),
                entity.getMaxRetries(),
                entity.getCreateTime(),
                entity.getUpdateTime());
    }
}
