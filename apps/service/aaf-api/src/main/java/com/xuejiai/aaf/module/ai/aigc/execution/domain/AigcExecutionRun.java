package com.xuejiai.aaf.module.ai.aigc.execution.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.module.ai.aigc.execution.api.AigcExecutionRunStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Agent、Tool、Workflow 动作的统一业务执行记录。 */
@Getter
@Setter
@Entity
@Table(name = "aigc_execution_run")
@SQLDelete(
        sql =
                "UPDATE aigc_execution_run SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class AigcExecutionRun extends BaseEntity {

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "object_id")
    private Long objectId;

    @Column(name = "parent_execution_run_id")
    private Long parentExecutionRunId;

    @Column(name = "root_execution_run_id")
    private Long rootExecutionRunId;

    @Column(name = "run_kind", nullable = false, length = 24)
    private String runKind = "ACTIVITY";

    @Column(name = "workflow_node_key", length = 100)
    private String workflowNodeKey;

    @Column(name = "execution_submission_id", nullable = false)
    private Long executionSubmissionId;

    @Column(name = "execution_reservation_id", nullable = false)
    private Long executionReservationId;

    @Column(name = "target_graph_revision", nullable = false)
    private Long targetGraphRevision;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "frozen_project_object_ids_json", nullable = false, columnDefinition = "jsonb")
    private List<Long> frozenProjectObjectIds = List.of();

    @Column(name = "binding_version_id")
    private Long bindingVersionId;

    @Column(name = "action_key", nullable = false, length = 100)
    private String actionKey;

    @Column(name = "target_type", nullable = false, length = 32)
    private String targetType;

    @Column(name = "target_ref", length = 200)
    private String targetRef;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AigcExecutionRunStatus status = AigcExecutionRunStatus.PENDING_BIND;

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
    private List<Long> attachmentRefs = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tool_calls", columnDefinition = "jsonb")
    private List<Map<String, Object>> toolCalls = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "context_snapshot", columnDefinition = "jsonb")
    private Map<String, Object> contextSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "effective_input_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> effectiveInput;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "output_payload", columnDefinition = "jsonb")
    private Map<String, Object> outputPayload;

    @Column(name = "cost_credits", precision = 12, scale = 2)
    private BigDecimal costCredits;

    @Column(name = "retry_of_execution_run_id")
    private Long retryOfExecutionRunId;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;
}
