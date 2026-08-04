package com.xuejiai.aaf.module.ai.aigc.work.domain;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 已采用且通过审核的项目交付成果登记。 */
@Getter
@Setter
@Entity
@Table(name = "aigc_work")
@SQLDelete(
        sql = "UPDATE aigc_work SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class AigcWork extends BaseEntity {

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "deliverable_object_id", nullable = false)
    private Long deliverableObjectId;

    @Column(name = "adopted_object_version_id", nullable = false)
    private Long adoptedObjectVersionId;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(name = "cover_media_version_id")
    private Long coverMediaVersionId;

    @Column(nullable = false, length = 32)
    private String status = "collected";

    @Column(nullable = false, length = 32)
    private String visibility = "PRIVATE";

    @Column(name = "user_id", nullable = false)
    private Long userId;
}
