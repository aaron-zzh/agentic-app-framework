package com.xuejiai.aaf.framework.intelligent.infrastructure.automation.persistence;

import java.time.Instant;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @Entity @Table(name="ai_automation_audit")
public class AutomationAuditEntity {
    @Id @Column(name="audit_id",length=128) private String auditId;
    @Column(name="tenant_id",nullable=false,length=128) private String tenantId;
    @Column(name="automation_id",nullable=false,length=128) private String automationId;
    @Column(nullable=false,length=64) private String action;
    @Column(name="actor_id",nullable=false,length=128) private String actorId;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="details",nullable=false,columnDefinition="jsonb") private Map<String,Object> details;
    @Column(name="occurred_at",nullable=false) private Instant occurredAt;
}
