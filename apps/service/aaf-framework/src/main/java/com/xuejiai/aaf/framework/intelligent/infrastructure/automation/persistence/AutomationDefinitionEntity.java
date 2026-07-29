package com.xuejiai.aaf.framework.intelligent.infrastructure.automation.persistence;

import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import com.xuejiai.aaf.framework.intelligent.automation.model.AutomationDefinition;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @Entity
@Table(name = "ai_automation_definition", uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "automation_id", "definition_version"}))
public class AutomationDefinitionEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name="tenant_id", nullable=false, length=128) private String tenantId;
    @Column(name="automation_id", nullable=false, length=128) private String automationId;
    @Column(name="definition_version", nullable=false) private Long definitionVersion;
    @Column(nullable=false, length=32) private String lifecycle;
    @Column(nullable=false) private Boolean enabled;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="definition_payload", nullable=false, columnDefinition="jsonb") private AutomationDefinition definition;
    @Column(name="created_at", nullable=false) private Instant createdAt;
    @Column(name="updated_at", nullable=false) private Instant updatedAt;
    @Version @Column(name="version", nullable=false) private Long version;
}
