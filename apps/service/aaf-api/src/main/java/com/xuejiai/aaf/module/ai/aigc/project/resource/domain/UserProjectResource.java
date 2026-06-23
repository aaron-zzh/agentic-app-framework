package com.xuejiai.aaf.module.ai.aigc.project.resource.domain;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** 项目-资源关联。资源类型：ASSISTANT/KNOWLEDGE_BASE/WORKFLOW/ASSET_GROUP/SKILL。 */
@Getter
@Setter
@Entity
@Table(
        name = "user_project_resource",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uq_upr_project_type_resource",
                    columnNames = {"project_id", "resource_type", "resource_id"})
        })
@SQLDelete(
        sql =
                "UPDATE user_project_resource SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class UserProjectResource extends BaseEntity {

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    /** 资源类型：ASSISTANT/KNOWLEDGE_BASE/WORKFLOW/ASSET_GROUP/SKILL */
    @Column(name = "resource_type", nullable = false, length = 20)
    private String resourceType;

    @Column(name = "resource_id", nullable = false)
    private Long resourceId;

    /** 关联角色：DEFAULT_ASSISTANT/REF/TARGET 等 */
    @Column(name = "role", length = 20)
    private String role;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;
}
