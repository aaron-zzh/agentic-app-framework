package com.xuejiai.aaf.module.content.domain;

import java.util.List;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.common.enums.content.ContentConfigStatusEnum;
import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.framework.crud.reference.CrudReference;
import com.xuejiai.aaf.framework.crud.reference.ReferenceCapability;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 项目蓝图实体。
 *
 * @author AaronZZH & Kiro
 */
@Getter
@Setter
@Entity
@Table(name = "cs_project_blueprint")
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
                "UPDATE cs_project_blueprint SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class ContentProjectBlueprint extends BaseEntity {

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "project_type_code", nullable = false, length = 64)
    private String projectTypeCode;

    @Column(name = "blueprint_version", nullable = false, length = 32)
    private String blueprintVersion;

    @Column(name = "production_mode", nullable = false, length = 32)
    private String productionMode;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "status", nullable = false, length = 32)
    private String status = ContentConfigStatusEnum.DRAFT.getCode();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "object_spec", columnDefinition = "jsonb")
    private Map<String, Object> objectSpec;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "relation_spec", columnDefinition = "jsonb")
    private Map<String, Object> relationSpec;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "deliverable_spec", columnDefinition = "jsonb")
    private Map<String, Object> deliverableSpec;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "action_keys", columnDefinition = "jsonb")
    private List<String> actionKeys = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "confirmation_gates", columnDefinition = "jsonb")
    private List<String> confirmationGates = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "brief_fields", columnDefinition = "jsonb")
    private List<String> briefFields = List.of();
}
