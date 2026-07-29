package com.xuejiai.aaf.module.content.domain;

import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.common.enums.content.ContentObjectSourceEnum;
import com.xuejiai.aaf.common.enums.content.ContentObjectStatusEnum;
import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.framework.crud.reference.CrudReference;
import com.xuejiai.aaf.framework.crud.reference.ReferenceCapability;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 项目对象实体。
 *
 * @author AaronZZH & Kiro
 */
@Getter
@Setter
@Entity
@Table(name = "cs_project_object")
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
                "UPDATE cs_project_object SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class ContentProjectObject extends BaseEntity {

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "object_type", nullable = false, length = 48)
    private String objectType;

    @Column(name = "object_key", nullable = false, length = 100)
    private String objectKey;

    @Column(name = "blueprint_node_key", length = 100)
    private String blueprintNodeKey;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "title", length = 300)
    private String title;

    @Column(name = "status", nullable = false, length = 32)
    private String status = ContentObjectStatusEnum.EMPTY.getCode();

    @Column(name = "source", nullable = false, length = 32)
    private String source = ContentObjectSourceEnum.USER.getCode();

    @Column(name = "schema_version", length = 32)
    private String schemaVersion;

    @Column(name = "entity_resource", length = 100)
    private String entityResource;

    @Column(name = "entity_id")
    private Long entityId;

    @Column(name = "adopted_version_ref", length = 200)
    private String adoptedVersionRef;

    @Column(name = "summary", length = 500)
    private String summary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb")
    private Map<String, Object> payload;
}
