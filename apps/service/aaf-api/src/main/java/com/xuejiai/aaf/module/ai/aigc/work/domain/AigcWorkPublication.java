package com.xuejiai.aaf.module.ai.aigc.work.domain;

import java.time.LocalDateTime;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 作品在固定渠道规格版本上的发布尝试；失败与重试均保留独立历史。 */
@Getter
@Setter
@Entity
@Table(name = "aigc_work_publication")
@SQLDelete(
        sql =
                "UPDATE aigc_work_publication SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class AigcWorkPublication extends BaseEntity {

    @Column(name = "work_id", nullable = false)
    private Long workId;

    @Column(name = "channel_spec_version_id", nullable = false)
    private Long channelSpecVersionId;

    @Column(name = "channel_code", nullable = false, length = 64)
    private String channelCode;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "retry_of_publication_id")
    private Long retryOfPublicationId;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    @Column(name = "external_id", length = 200)
    private String externalId;

    @Column(name = "external_url", length = 1000)
    private String externalUrl;

    @Column(nullable = false, length = 32)
    private String status = "PENDING";

    @Column(name = "scheduled_time")
    private LocalDateTime scheduledTime;

    @Column(name = "published_time")
    private LocalDateTime publishedTime;

    @Column(name = "failure_code", length = 100)
    private String failureCode;

    @Column(name = "failure_message", length = 1000)
    private String failureMessage;

    @Column(name = "canceled_time")
    private LocalDateTime canceledTime;

    @Column(name = "cancel_idempotency_key", length = 100)
    private String cancelIdempotencyKey;

    @Column(name = "cancel_request_hash", length = 64)
    private String cancelRequestHash;

    @Column(name = "cancel_reason", length = 1000)
    private String cancelReason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_payload", columnDefinition = "jsonb")
    private Map<String, Object> responsePayload;
}
