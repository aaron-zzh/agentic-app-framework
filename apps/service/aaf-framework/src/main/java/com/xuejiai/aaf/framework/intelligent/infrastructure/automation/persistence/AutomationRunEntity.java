package com.xuejiai.aaf.framework.intelligent.infrastructure.automation.persistence;

import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import com.xuejiai.aaf.framework.intelligent.automation.model.AutomationRun;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @Entity
@Table(name="ai_automation_run", uniqueConstraints={@UniqueConstraint(columnNames={"tenant_id","automation_id","trigger_key"}), @UniqueConstraint(columnNames={"tenant_id","run_id"})})
public class AutomationRunEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="tenant_id",nullable=false,length=128) private String tenantId;
    @Column(name="run_id",nullable=false,length=128) private String runId;
    @Column(name="automation_id",nullable=false,length=128) private String automationId;
    @Column(name="definition_version",nullable=false) private Long definitionVersion;
    @Column(name="trigger_key",nullable=false,length=256) private String triggerKey;
    @Column(name="delegated_task_id",nullable=false,length=128) private String delegatedTaskId;
    @Column(nullable=false,length=32) private String status;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="run_payload",nullable=false,columnDefinition="jsonb") private AutomationRun run;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    @Version @Column(name="version",nullable=false) private Long version;
}
