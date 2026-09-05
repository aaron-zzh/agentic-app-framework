package com.xuejiai.aaf.module.ai.aigc.execution.domain;

import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 动作提交的 durable intent/outbox，是 saga 恢复的唯一入口。 */
@Getter
@Setter
@Entity
@Table(name = "aigc_execution_submission")
public class AigcExecutionSubmission extends BaseEntity {

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_payload", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> requestPayload;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "INTENT_RECORDED";

    @Column(name = "reservation_id")
    private Long reservationId;

    @Column(name = "root_execution_run_id")
    private Long rootExecutionRunId;

    @Column(name = "last_error", length = 1000)
    private String lastError;
}
