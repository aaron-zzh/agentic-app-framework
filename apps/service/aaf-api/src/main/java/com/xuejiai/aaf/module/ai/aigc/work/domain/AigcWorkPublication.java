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

/** 作品在固定渠道规格版本上的发布记录。 */
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

    @Column(name = "external_id", length = 200)
    private String externalId;

    @Column(name = "external_url", length = 1000)
    private String externalUrl;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "scheduled_time")
    private LocalDateTime scheduledTime;

    @Column(name = "published_time")
    private LocalDateTime publishedTime;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_payload", columnDefinition = "jsonb")
    private Map<String, Object> responsePayload;
}
