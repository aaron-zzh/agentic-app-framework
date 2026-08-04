package com.xuejiai.aaf.module.ai.aigc.project.domain;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 项目或项目对象对媒体版本的用途引用。 */
@Getter
@Setter
@Entity
@Table(name = "aigc_project_media_ref")
@SQLDelete(
        sql =
                "UPDATE aigc_project_media_ref SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class AigcProjectMediaRef extends BaseEntity {

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "object_id")
    private Long objectId;

    @Column(name = "object_version_id")
    private Long objectVersionId;

    @Column(name = "media_version_id", nullable = false)
    private Long mediaVersionId;

    @Column(nullable = false, length = 32)
    private String role;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "adoption_status", nullable = false, length = 32)
    private String adoptionStatus = "candidate";
}
