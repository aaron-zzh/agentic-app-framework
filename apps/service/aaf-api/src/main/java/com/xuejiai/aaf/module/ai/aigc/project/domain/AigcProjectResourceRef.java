package com.xuejiai.aaf.module.ai.aigc.project.domain;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 项目对 Assistant、Knowledge、Workflow、Work 等资源的稳定引用。 */
@Getter
@Setter
@Entity
@Table(name = "aigc_project_resource_ref")
@SQLDelete(
        sql =
                "UPDATE aigc_project_resource_ref SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class AigcProjectResourceRef extends BaseEntity {

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "resource_type", nullable = false, length = 32)
    private String resourceType;

    @Column(name = "resource_id", nullable = false, length = 128)
    private String resourceId;

    @Column(name = "resource_version", length = 64)
    private String resourceVersion;

    @Column(length = 32)
    private String role;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;
}
