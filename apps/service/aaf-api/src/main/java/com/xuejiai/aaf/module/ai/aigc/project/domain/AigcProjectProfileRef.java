package com.xuejiai.aaf.module.ai.aigc.project.domain;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 项目对品牌资料精确版本的引用。 */
@Getter
@Setter
@Entity
@Table(name = "aigc_project_profile_ref")
@SQLDelete(
        sql =
                "UPDATE aigc_project_profile_ref SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class AigcProjectProfileRef extends BaseEntity {

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "brand_profile_version_id", nullable = false)
    private Long brandProfileVersionId;

    @Column(name = "ref_scope", nullable = false, length = 32)
    private String refScope;

    @Column(name = "scope_note", length = 500)
    private String scopeNote;
}
