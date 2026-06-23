package com.xuejiai.aaf.module.ai.aigc.workflow.domain;

import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** 用户工作流模板（流水线）——v0.2.1 仅官方只读，用户级运行不创建。 */
@Getter
@Setter
@Entity
@Table(name = "user_workflow_template")
@SQLDelete(
        sql =
                "UPDATE user_workflow_template SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class UserWorkflowTemplate extends BaseEntity {

    @Column(name = "code", nullable = false, length = 100, unique = true)
    private String code;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "cover_url", length = 1000)
    private String coverUrl;

    /** 模板分类：CONTENT/MARKETING/STUDY/LIFE */
    @Column(name = "category", nullable = false, length = 50)
    private String category;

    /** 模板配置，含 steps 数组 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "template_config", columnDefinition = "jsonb")
    private Map<String, Object> templateConfig;

    @Column(name = "is_official", nullable = false)
    private Boolean isOfficial = false;

    @Column(name = "usage_count", nullable = false)
    private Integer usageCount = 0;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    public void incrementUsage() {
        this.usageCount++;
    }
}
