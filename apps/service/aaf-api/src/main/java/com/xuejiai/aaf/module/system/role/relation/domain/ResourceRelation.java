package com.xuejiai.aaf.module.system.role.relation.domain;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * ReBAC 资源关系——记录主体与资源之间的关系。
 *
 * @author AaronZZH & Kiro
 */
@Getter
@Setter
@Entity
@Table(name = "sys_resource_relation")
@SQLDelete(
        sql =
                "UPDATE sys_resource_relation SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class ResourceRelation extends BaseEntity {

    /** 资源类型（如 document、project） */
    @Column(name = "resource_type", nullable = false, length = 50)
    private String resourceType;

    /** 资源 ID */
    @Column(name = "resource_id", nullable = false)
    private Long resourceId;

    /** 关系类型 */
    @Enumerated(EnumType.STRING)
    @Column(name = "relation", nullable = false, length = 20)
    private RelationType relation;

    /** 主体类型 */
    @Enumerated(EnumType.STRING)
    @Column(name = "subject_type", nullable = false, length = 20)
    private SubjectType subjectType;

    /** 主体 ID */
    @Column(name = "subject_id", nullable = false)
    private Long subjectId;

    /** 关系类型枚举 */
    public enum RelationType {
        OWNER,
        EDITOR,
        VIEWER
    }

    /** 主体类型枚举 */
    public enum SubjectType {
        USER,
        ROLE,
        AGENT
    }
}
