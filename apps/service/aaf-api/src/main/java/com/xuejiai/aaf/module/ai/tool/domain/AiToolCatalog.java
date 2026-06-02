package com.xuejiai.aaf.module.ai.tool.domain;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** AI 工具目录。代码 ToolCallback 决定能力边界，本表决定开放与治理策略。 */
@Getter
@Setter
@Entity
@Table(name = "ai_tool_catalog")
@SQLDelete(
        sql =
                "UPDATE ai_tool_catalog SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ? AND version = ?")
public class AiToolCatalog extends BaseEntity {

    @Column(name = "tool_name", nullable = false, unique = true, length = 120)
    private String toolName;

    @Column(name = "source", nullable = false, length = 32)
    private String source = "LOCAL";

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;

    @Column(name = "tool_type", nullable = false, length = 32)
    private String toolType = "FUNCTION";

    @Column(name = "category", length = 64)
    private String category;

    @Column(name = "risk_level", nullable = false, length = 16)
    private String riskLevel = "LOW";

    @Column(name = "read_only", nullable = false)
    private Boolean readOnly = false;

    @Column(name = "require_confirm", nullable = false)
    private Boolean requireConfirm = false;

    @Column(name = "permission_code", length = 120)
    private String permissionCode;

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
