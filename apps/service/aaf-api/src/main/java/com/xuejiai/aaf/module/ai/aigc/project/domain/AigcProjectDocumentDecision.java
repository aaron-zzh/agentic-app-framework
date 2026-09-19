package com.xuejiai.aaf.module.ai.aigc.project.domain;

import java.time.LocalDateTime;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 项目自产文档精确版本的 candidate 与采用/否决审计记录。 */
@Getter
@Setter
@Entity
@Table(name = "aigc_project_document_decision")
public class AigcProjectDocumentDecision extends BaseEntity {

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "project_document_ref_id", nullable = false)
    private Long projectDocumentRefId;

    @Column(name = "document_version_id", nullable = false)
    private Long documentVersionId;

    @Column(name = "replaced_document_version_id")
    private Long replacedDocumentVersionId;

    @Column(nullable = false, length = 16)
    private String decision = "PENDING";

    @Column(name = "source_type", nullable = false, length = 16)
    private String sourceType;

    @Column(name = "source_ref", length = 255)
    private String sourceRef;

    @Column(name = "candidate_idempotency_key", nullable = false, length = 100)
    private String candidateIdempotencyKey;

    @Column(name = "candidate_request_hash", nullable = false, length = 64)
    private String candidateRequestHash;

    @Column(name = "decision_idempotency_key", length = 100)
    private String decisionIdempotencyKey;

    @Column(name = "decision_request_hash", length = 64)
    private String decisionRequestHash;

    @Column(name = "decision_mode", length = 32)
    private String decisionMode;

    @Column(name = "decision_reason", length = 1000)
    private String decisionReason;

    @Column(name = "decided_by")
    private Long decidedBy;

    @Column(name = "decided_by_type", length = 16)
    private String decidedByType;

    @Column(name = "decided_time")
    private LocalDateTime decidedTime;
}
