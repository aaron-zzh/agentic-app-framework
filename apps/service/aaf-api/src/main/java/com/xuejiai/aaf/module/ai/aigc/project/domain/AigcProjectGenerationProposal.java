package com.xuejiai.aaf.module.ai.aigc.project.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 项目生成目标的版本化提案；只表达提议与确认，不拥有 Task/Run 调度状态。 */
@Getter
@Setter
@Entity
@Table(name = "aigc_project_generation_proposal")
public class AigcProjectGenerationProposal extends BaseEntity {

    /** 作用域：PROJECT（已物化项目）或 CREATION_DRAFT（物化前草稿），二者字段互斥并由 DB CHECK 兜底。 */
    @Column(name = "scope_kind", nullable = false, length = 32)
    private String scopeKind;

    @Column(name = "project_id")
    private Long projectId;

    /** CREATION_DRAFT 的服务端签发身份；物化后不迁移，仅通过 linkedProjectId 追溯。 */
    @Column(name = "draft_id", length = 64)
    private String draftId;

    @Column(name = "blueprint_version_id")
    private Long blueprintVersionId;

    /** 草稿 Proposal 被用于物化后回填的项目引用；只作追溯，不参与确认判定。 */
    @Column(name = "linked_project_id")
    private Long linkedProjectId;

    @Column(name = "proposal_id", nullable = false, length = 128)
    private String proposalId;

    @Column(name = "proposal_version", nullable = false)
    private Integer proposalVersion;

    @Column(name = "proposal_hash", nullable = false, length = 64)
    private String proposalHash;

    @Column(name = "context_hash", nullable = false, length = 64)
    private String contextHash;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "source_type", nullable = false, length = 32)
    private String sourceType;

    @Column(name = "source_ref", length = 255)
    private String sourceRef;

    @Column(name = "expected_project_version")
    private Integer expectedProjectVersion;

    @Column(name = "estimated_credits", precision = 12, scale = 2)
    private BigDecimal estimatedCredits;

    @Column(name = "confirmation_policy", nullable = false, length = 32)
    private String confirmationPolicy;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "approved_target_keys", columnDefinition = "jsonb")
    private List<String> approvedTargetKeys = List.of();

    @Column(name = "approval_idempotency_key", length = 100)
    private String approvalIdempotencyKey;

    @Column(name = "approval_request_hash", length = 64)
    private String approvalRequestHash;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "approved_by_type", length = 16)
    private String approvedByType;

    @Column(name = "approved_time")
    private LocalDateTime approvedTime;

    @Column(name = "linked_task_id", length = 128)
    private String linkedTaskId;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;
}
