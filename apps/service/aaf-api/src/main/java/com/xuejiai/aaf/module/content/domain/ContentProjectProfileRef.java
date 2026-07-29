package com.xuejiai.aaf.module.content.domain;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.framework.crud.reference.CrudReference;
import com.xuejiai.aaf.framework.crud.reference.ReferenceCapability;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 项目资料引用实体。
 *
 * @author AaronZZH & Kiro
 */
@Getter
@Setter
@Entity
@Table(name = "cs_project_profile_ref")
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
                "UPDATE cs_project_profile_ref SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class ContentProjectProfileRef extends BaseEntity {

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "brand_profile_id", nullable = false)
    private Long brandProfileId;

    @Column(name = "ref_scope", nullable = false, length = 32)
    private String refScope;

    @Column(name = "profile_version", length = 32)
    private String profileVersion;

    @Column(name = "scope_note", length = 500)
    private String scopeNote;
}
