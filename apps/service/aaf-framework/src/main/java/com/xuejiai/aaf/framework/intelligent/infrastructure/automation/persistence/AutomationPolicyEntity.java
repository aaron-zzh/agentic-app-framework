package com.xuejiai.aaf.framework.intelligent.infrastructure.automation.persistence;

import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import com.xuejiai.aaf.framework.intelligent.automation.model.AutomationGovernance.OrganizationPolicy;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @Entity @Table(name="ai_automation_policy")
public class AutomationPolicyEntity {
    @Id @Column(name="tenant_id",length=128) private String tenantId;
    @Column(name="global_stop",nullable=false) private Boolean globalStop;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="policy_payload",nullable=false,columnDefinition="jsonb") private OrganizationPolicy policy;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    @Version @Column(name="version",nullable=false) private Long version;
}
