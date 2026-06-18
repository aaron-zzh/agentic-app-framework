package com.xuejiai.aaf.module.ai.action.domain;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** AI 动作目录。代码 Adapter 决定能力边界，本表决定是否开放和治理策略。 */
@Getter
@Setter
@Entity
@Table(name = "ai_action_catalog")
@SQLDelete(
        sql =
                "UPDATE ai_action_catalog SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class AiActionCatalog extends BaseEntity {

    @Column(name = "action_key", nullable = false, length = 120)
    private String actionKey;

    @Column(name = "entity_slug", length = 120)
    private String entitySlug;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;

    @Column(name = "risk_level", nullable = false, length = 16)
    private String riskLevel = "LOW";

    @Column(name = "require_confirm", nullable = false)
    private Boolean requireConfirm = false;

    @Column(name = "permission_code_override", length = 120)
    private String permissionCodeOverride;

    @Column(name = "entitlement_code", length = 64)
    private String entitlementCode;

    @Column(name = "cost_expression", length = 255)
    private String costExpression;

    @Column(name = "input_schema")
    private String inputSchema;

    @Column(name = "output_schema")
    private String outputSchema;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 100;
}
