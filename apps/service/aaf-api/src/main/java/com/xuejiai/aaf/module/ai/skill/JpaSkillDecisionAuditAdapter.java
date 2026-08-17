package com.xuejiai.aaf.module.ai.skill;

import java.time.Instant;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.framework.intelligent.assistant.model.SkillDecisionAuditEvent;
import com.xuejiai.aaf.framework.intelligent.assistant.port.SkillDecisionAuditPort;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

/** 将 framework Skill 决策事件追加到审计表。 */
@Component
@RequiredArgsConstructor
class JpaSkillDecisionAuditAdapter implements SkillDecisionAuditPort {

    private final SkillDecisionAuditRepository repository;

    @Override
    @Transactional
    public void append(SkillDecisionAuditEvent event) {
        repository.save(SkillDecisionAuditEntity.from(event));
    }
}

@Repository
interface SkillDecisionAuditRepository extends JpaRepository<SkillDecisionAuditEntity, Long> {}

@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "ai_skill_decision_audit")
class SkillDecisionAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", length = 128)
    private String tenantId;

    @Column(name = "org_id")
    private Long orgId;

    @Column(name = "workspace_id")
    private Long workspaceId;

    @Column(name = "assistant_id", length = 128)
    private String assistantId;

    @Column(name = "conversation_id", length = 128)
    private String conversationId;

    @Column(name = "session_id", length = 128)
    private String sessionId;

    @Column(name = "task_id", length = 128)
    private String taskId;

    @Column(name = "execution_id", length = 128)
    private String executionId;

    @Column(name = "run_id", length = 128)
    private String runId;

    @Column(name = "role_key", length = 128)
    private String roleKey;

    @Column(name = "selection_mode", length = 32)
    private String selectionMode;

    @Column(name = "event_type", nullable = false, length = 48)
    private String eventType;

    @Column(name = "skill_code", length = 100)
    private String skillCode;

    @Column(name = "skill_version_id")
    private Long skillVersionId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "candidate_skill_codes_json", columnDefinition = "jsonb")
    private List<String> candidateSkillCodes;

    @Column(name = "reason_code", length = 128)
    private String reasonCode;

    @Column(name = "reason_detail", length = 1024)
    private String reasonDetail;

    @Column(name = "selected_by", length = 32)
    private String selectedBy;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "effective_tool_refs_json", columnDefinition = "jsonb")
    private List<String> effectiveToolRefs;

    @Column(name = "model_spec", length = 256)
    private String modelSpec;

    @Column(name = "trace_id", length = 128)
    private String traceId;

    @Column(name = "correlation_id", length = 128)
    private String correlationId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    static SkillDecisionAuditEntity from(SkillDecisionAuditEvent event) {
        var entity = new SkillDecisionAuditEntity();
        entity.setTenantId(event.tenantId());
        entity.setOrgId(event.orgId());
        entity.setWorkspaceId(event.workspaceId());
        entity.setAssistantId(event.assistantId());
        entity.setConversationId(event.conversationId());
        entity.setSessionId(event.sessionId());
        entity.setTaskId(event.taskId());
        entity.setExecutionId(event.executionId());
        entity.setRunId(event.runId());
        entity.setRoleKey(event.roleKey());
        entity.setSelectionMode(event.selectionMode());
        entity.setEventType(event.eventType());
        entity.setSkillCode(event.skillCode());
        entity.setSkillVersionId(event.skillVersionId());
        entity.setCandidateSkillCodes(event.candidateSkillCodes());
        entity.setReasonCode(event.reasonCode());
        entity.setReasonDetail(event.reasonDetail());
        entity.setSelectedBy(event.selectedBy());
        entity.setEffectiveToolRefs(event.effectiveToolRefs());
        entity.setModelSpec(event.modelSpec());
        entity.setTraceId(event.traceId());
        entity.setCorrelationId(event.correlationId());
        entity.setCreatedAt(event.occurredAt());
        return entity;
    }
}
