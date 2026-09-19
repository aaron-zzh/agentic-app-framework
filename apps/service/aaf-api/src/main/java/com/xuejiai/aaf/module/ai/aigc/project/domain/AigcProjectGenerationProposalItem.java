package com.xuejiai.aaf.module.ai.aigc.project.domain;

import java.math.BigDecimal;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Proposal 内单项目标的不可变指令、依赖和输入引用。 */
@Getter
@Setter
@Entity
@Table(name = "aigc_project_generation_proposal_item")
public class AigcProjectGenerationProposalItem extends BaseEntity {

    @Column(name = "proposal_record_id", nullable = false)
    private Long proposalRecordId;

    @Column(name = "proposal_id", nullable = false, length = 128)
    private String proposalId;

    @Column(name = "proposal_version", nullable = false)
    private Integer proposalVersion;

    @Column(name = "item_key", nullable = false, length = 128)
    private String itemKey;

    @Column(name = "target_type", nullable = false, length = 32)
    private String targetType;

    @Column(name = "target_key", nullable = false, length = 128)
    private String targetKey;

    @Column(name = "action_key", length = 100)
    private String actionKey;

    @Column(name = "generation_instruction", nullable = false, columnDefinition = "text")
    private String generationInstruction;

    @Column(name = "instruction_hash", nullable = false, length = 64)
    private String instructionHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dependency_keys", nullable = false, columnDefinition = "jsonb")
    private List<String> dependencyKeys = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "required_context_keys", nullable = false, columnDefinition = "jsonb")
    private List<String> requiredContextKeys = List.of();

    @Column(name = "model_policy", length = 100)
    private String modelPolicy;

    @Column(name = "estimated_credits", precision = 12, scale = 2)
    private BigDecimal estimatedCredits;

    @Column(name = "confirmation_policy", nullable = false, length = 32)
    private String confirmationPolicy;

    @Column(name = "item_version", nullable = false)
    private Integer itemVersion;

    @Column(name = "item_hash", nullable = false, length = 64)
    private String itemHash;
}
