package com.xuejiai.aaf.module.content.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.common.enums.content.ContentExecutionStatusEnum;
import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.framework.crud.reference.CrudReference;
import com.xuejiai.aaf.framework.crud.reference.ReferenceCapability;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 执行记录实体。
 *
 * @author AaronZZH & Kiro
 */
@Getter
@Setter
@Entity
@Table(name = "cs_execution_run")
@CrudReference(
        key = "createBy",
        idProperty = "createBy",
        targetResource = "system.user",
        viewField = "createBy",
        capabilities = ReferenceCapability.READ)
@CrudReference(
        key = "updateBy",
        idProperty = "updateBy",
        targetResource = "system.user",
        viewField = "updateBy",
        capabilities = ReferenceCapability.READ)
@SQLDelete(
        sql =
                "UPDATE cs_execution_run SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class ContentExecutionRun extends BaseEntity {

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "object_id")
    private Long objectId;

    @Column(name = "action_key", nullable = false, length = 100)
    private String actionKey;

    @Column(name = "target_type", nullable = false, length = 32)
    private String targetType;

    @Column(name = "target_ref", length = 200)
    private String targetRef;

    @Column(name = "status", nullable = false, length = 32)
    private String status = ContentExecutionStatusEnum.PENDING.getCode();

    @Column(name = "generation_mode", length = 32)
    private String generationMode;

    @Column(name = "role_profile_code", length = 64)
    private String roleProfileCode;

    @Column(name = "model_policy_version", length = 64)
    private String modelPolicyVersion;

    @Column(name = "selected_model_version", length = 100)
    private String selectedModelVersion;

    @Column(name = "skill_definition_version_id", length = 64)
    private String skillDefinitionVersionId;

    @Column(name = "prompt_text", columnDefinition = "text")
    private String promptText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snippet_refs", columnDefinition = "jsonb")
    private List<String> snippetRefs = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attachment_refs", columnDefinition = "jsonb")
    private List<String> attachmentRefs = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tool_calls", columnDefinition = "jsonb")
    private List<Map<String, Object>> toolCalls = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_payload", columnDefinition = "jsonb")
    private Map<String, Object> inputPayload;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "output_payload", columnDefinition = "jsonb")
    private Map<String, Object> outputPayload;

    @Column(name = "cost_credits", precision = 12, scale = 2)
    private BigDecimal costCredits;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "aigc_task_id")
    private Long aigcTaskId;
}
