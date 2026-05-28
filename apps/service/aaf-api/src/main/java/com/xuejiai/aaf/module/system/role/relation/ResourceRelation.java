/**
 * 资源关系实体——ReBAC 关系元组。
 *
 * <p>表达"某主体对某资源拥有某种关系"，如 user:1 是 document:42 的 EDITOR。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.module.system.role.relation;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "sys_resource_relation", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"resource_type", "resource_id", "relation", "subject_type", "subject_id"})
})
public class ResourceRelation extends BaseEntity {

    /** 资源类型（如 document/knowledge_base/agent） */
    @Column(name = "resource_type", nullable = false, length = 64)
    private String resourceType;

    /** 资源 ID */
    @Column(name = "resource_id", nullable = false)
    private Long resourceId;

    /** 关系类型（OWNER/EDITOR/VIEWER） */
    @Column(name = "relation", nullable = false, length = 32)
    private String relation;

    /** 主体类型（USER/ROLE/AGENT） */
    @Column(name = "subject_type", nullable = false, length = 32)
    private String subjectType;

    /** 主体 ID */
    @Column(name = "subject_id", nullable = false)
    private Long subjectId;
}
