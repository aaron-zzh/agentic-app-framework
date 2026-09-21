package com.xuejiai.aaf.module.ai.aigc.project.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectBlueprintSnapshotPayload;
import com.xuejiai.aaf.module.ai.aigc.project.api.AssistantBlueprintCreationContext;
import com.xuejiai.aaf.module.ai.aigc.project.api.AssistantBlueprintProposalInput;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Assistant 生成项目结构的版本化提案；不复用内容生成 Proposal，也不承担 Task 调度状态。 */
@Getter
@Setter
@Entity
@Table(name = "aigc_assistant_blueprint_proposal")
public class AssistantBlueprintProposal extends BaseEntity {

    @Column(name = "planning_id", nullable = false, length = 64)
    private String planningId;

    @Column(name = "proposal_id", nullable = false, length = 128)
    private String proposalId;

    @Column(name = "proposal_version", nullable = false)
    private Integer proposalVersion;

    @Column(name = "proposal_hash", length = 64)
    private String proposalHash;

    @Column(name = "project_name", nullable = false, length = 200)
    private String projectName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "creation_context", nullable = false, columnDefinition = "jsonb")
    private AssistantBlueprintCreationContext creationContext;

    @Column(name = "planning_instruction", nullable = false, length = 4000)
    private String planningInstruction;

    @Column(name = "assistant_id", nullable = false, length = 128)
    private String assistantId;

    @Column(nullable = false, length = 32)
    private String status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_proposal", columnDefinition = "jsonb")
    private AssistantBlueprintProposalInput rawProposal;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "normalized_snapshot", columnDefinition = "jsonb")
    private AigcProjectBlueprintSnapshotPayload normalizedSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validation_result", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> validationResult = Map.of();

    @Column(name = "validator_version", nullable = false, length = 32)
    private String validatorVersion;

    @Column(name = "semantic_catalog_version", length = 64)
    private String semanticCatalogVersion;

    @Column(name = "estimated_credits", precision = 12, scale = 2)
    private BigDecimal estimatedCredits;

    @Column(name = "source_task_id", nullable = false, length = 128)
    private String sourceTaskId;

    @Column(name = "source_execution_id", length = 128)
    private String sourceExecutionId;

    @Column(name = "confirmed_by")
    private Long confirmedBy;

    @Column(name = "confirmed_by_type", length = 16)
    private String confirmedByType;

    @Column(name = "confirmed_time")
    private LocalDateTime confirmedTime;

    @Column(name = "confirmation_receipt_hash", length = 64)
    private String confirmationReceiptHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "linked_project_id")
    private Long linkedProjectId;
}
