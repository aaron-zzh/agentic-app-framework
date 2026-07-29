package com.xuejiai.aaf.module.content.domain;

import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.framework.crud.reference.CrudReference;
import com.xuejiai.aaf.framework.crud.reference.ReferenceCapability;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 项目关系实体。
 *
 * @author AaronZZH & Kiro
 */
@Getter
@Setter
@Entity
@Table(name = "cs_project_relation")
@CrudReference(
        key = "createBy",
        idProperty = "createBy",
        targetResource = "system.user",
        viewField = "createBy",
        capabilities = ReferenceCapability.READ)
@CrudReference(
        key = "updateBy",
        idProperty = "updateBy",
        targetResource = "system.user",
        viewField = "updateBy",
        capabilities = ReferenceCapability.READ)
@SQLDelete(
        sql =
                "UPDATE cs_project_relation SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class ContentProjectRelation extends BaseEntity {

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "relation_type", nullable = false, length = 32)
    private String relationType;

    @Column(name = "layer", nullable = false, length = 32)
    private String layer;

    @Column(name = "source_object_id", nullable = false)
    private Long sourceObjectId;

    @Column(name = "target_object_id", nullable = false)
    private Long targetObjectId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "relation_meta", columnDefinition = "jsonb")
    private Map<String, Object> relationMeta;
}
