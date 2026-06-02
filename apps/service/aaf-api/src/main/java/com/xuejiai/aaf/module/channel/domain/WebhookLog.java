package com.xuejiai.aaf.module.channel.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Webhook 推送日志。
 *
 * <p>记录每次 Webhook 推送的请求/响应/状态，用于排查和重试。
 */
@Getter
@Setter
@Entity
@Table(name = "webhook_log")
@SQLDelete(
        sql = "UPDATE webhook_log SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class WebhookLog extends BaseEntity {

    /** 关联 webhook_config ID */
    @Column(name = "webhook_id", nullable = false)
    private Long webhookId;

    /** 事件类型 */
    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    /** 请求体（JSON） */
    @Column(name = "request_body", columnDefinition = "text")
    private String requestBody;

    /** 响应状态码 */
    @Column(name = "response_status")
    private Integer responseStatus;

    /** 响应体（截断保存） */
    @Column(name = "response_body", length = 2000)
    private String responseBody;

    /** 推送状态：success/failed/pending */
    @Column(name = "status", nullable = false, length = 16)
    private String status = "pending";

    /** 失败原因 */
    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    /** 重试次数 */
    @Column(name = "retry_count")
    private Integer retryCount = 0;

    /** 下次重试时间 */
    @Column(name = "next_retry_time")
    private LocalDateTime nextRetryTime;

    /** 推送时间 */
    @Column(name = "push_time")
    private LocalDateTime pushTime;
}
