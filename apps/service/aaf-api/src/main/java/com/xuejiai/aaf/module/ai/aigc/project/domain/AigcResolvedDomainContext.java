package com.xuejiai.aaf.module.ai.aigc.project.domain;

import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 解析后的项目领域上下文不可变修订。 */
@Getter
@Setter
@Entity
@Table(name = "aigc_resolved_domain_context")
@SQLDelete(
        sql =
                "UPDATE aigc_resolved_domain_context SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class AigcResolvedDomainContext extends BaseEntity {

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "revision_no", nullable = false)
    private Integer revisionNo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_facts", columnDefinition = "jsonb")
    private Map<String, Object> inputFacts;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "resolved_rules", columnDefinition = "jsonb")
    private Map<String, Object> resolvedRules;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "resolution_result", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> resolutionResult;
}
