package com.xuejiai.aaf.framework.intelligent.infrastructure.automation.persistence;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.framework.intelligent.automation.model.AutomationGovernance.DefinitionLifecycle;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
        name = "ai_definition_lifecycle",
        uniqueConstraints =
                @UniqueConstraint(
                        columnNames = {
                            "tenant_id",
                            "definition_kind",
                            "definition_id",
                            "definition_version"
                        }))
public class DefinitionLifecycleEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 128)
    private String tenantId;

    @Column(name = "definition_kind", nullable = false, length = 32)
    private String definitionKind;

    @Column(name = "definition_id", nullable = false, length = 128)
    private String definitionId;

    @Column(name = "definition_version", nullable = false)
    private Long definitionVersion;

    @Column(nullable = false, length = 32)
    private String state;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "lifecycle_payload", nullable = false, columnDefinition = "jsonb")
    private DefinitionLifecycle lifecycle;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
