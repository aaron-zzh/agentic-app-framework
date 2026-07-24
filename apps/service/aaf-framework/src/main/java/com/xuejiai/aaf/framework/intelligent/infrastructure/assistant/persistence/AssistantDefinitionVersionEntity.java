package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantDefinition;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/** Assistant 不可变版本快照的 JPA 实体。 */
@Getter
@Setter
@Entity
@Table(
        name = "ai_assistant_definition_version",
        uniqueConstraints = {
            @UniqueConstraint(
                    columnNames = {"tenant_id", "assistant_id", "definition_version"}),
            @UniqueConstraint(
                    columnNames = {"tenant_id", "system_key", "definition_version"})
        })
public class AssistantDefinitionVersionEntity {

    static final String SYSTEM_TENANT_ID = "__system__";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 128)
    private String tenantId;

    @Column(name = "assistant_id", nullable = false, length = 128)
    private String assistantId;

    @Column(name = "system_key", length = 128)
    private String systemKey;

    @Column(name = "definition_version", nullable = false)
    private Long definitionVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "definition_payload", nullable = false, columnDefinition = "jsonb")
    private AssistantDefinition definition;
}
