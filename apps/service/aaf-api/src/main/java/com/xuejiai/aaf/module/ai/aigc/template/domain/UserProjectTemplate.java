package com.xuejiai.aaf.module.ai.aigc.template.domain;

import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** 项目模板——官方或用户自建，支持 fork 创建新项目。 */
@Getter
@Setter
@Entity
@Table(name = "user_project_template")
@SQLDelete(
        sql =
                "UPDATE user_project_template SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class UserProjectTemplate extends BaseEntity {

    @Column(name = "code", nullable = false, length = 100, unique = true)
    private String code;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "cover_url", length = 1000)
    private String coverUrl;

    /** 模板分类：LIFE/STUDY/WORK/CONTENT_OPS/AIGC */
    @Column(name = "category", nullable = false, length = 50)
    private String category;

    /** 对应 aigc_project.type */
    @Column(name = "project_type", nullable = false, length = 30)
    private String projectType;

    /** 模板配置，含 prompt/defaultPersonaId/defaultKbIds 等 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "template_config", columnDefinition = "jsonb")
    private Map<String, Object> templateConfig;

    @Column(name = "is_official", nullable = false)
    private Boolean isOfficial = false;

    @Column(name = "usage_count", nullable = false)
    private Integer usageCount = 0;

    /** 官方模板=NULL，用户自建=userId（v0.2 启用） */
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    public void incrementUsage() {
        this.usageCount++;
    }
}
