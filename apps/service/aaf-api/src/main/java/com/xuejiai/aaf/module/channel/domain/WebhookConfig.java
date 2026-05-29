package com.xuejiai.aaf.module.channel.domain;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Webhook 配置。
 *
 * <p>定义出站 Webhook 推送目标和入站 Webhook 接收配置。
 */
@Getter
@Setter
@Entity
@Table(name = "webhook_config")
@SQLDelete(
        sql =
                "UPDATE webhook_config SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class WebhookConfig extends BaseEntity {

    /** Webhook 名称 */
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** 目标 URL */
    @Column(name = "url", nullable = false, length = 500)
    private String url;

    /** 订阅的事件类型（逗号分隔，如 message.received,user.created） */
    @Column(name = "event_types", length = 500)
    private String eventTypes;

    /** HMAC 签名密钥 */
    @Column(name = "secret", length = 200)
    private String secret;

    /** 状态：active/inactive/failed */
    @Column(name = "status", nullable = false, length = 16)
    private String status = "active";

    /** 方向：outbound（推送到外部）/ inbound（接收外部推送） */
    @Column(name = "direction", nullable = false, length = 16)
    private String direction = "outbound";

    /** 连续失败次数 */
    @Column(name = "failure_count")
    private Integer failureCount = 0;

    /** 最大重试次数 */
    @Column(name = "max_retries")
    private Integer maxRetries = 3;
}
