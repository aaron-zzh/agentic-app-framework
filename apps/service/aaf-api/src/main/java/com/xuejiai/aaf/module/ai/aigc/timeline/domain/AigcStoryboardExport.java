package com.xuejiai.aaf.module.ai.aigc.timeline.domain;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Shot/ShotKeyframe 项目投影生成的只读导出快照。 */
@Getter
@Setter
@Entity
@Table(name = "aigc_storyboard_export")
@SQLDelete(
        sql =
                "UPDATE aigc_storyboard_export SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class AigcStoryboardExport extends BaseEntity {

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "source_revision_no", nullable = false)
    private Integer sourceRevisionNo;

    @Column(name = "deliverable_object_id")
    private Long deliverableObjectId;

    @Column(name = "export_media_version_id", nullable = false)
    private Long exportMediaVersionId;

    @Column(name = "export_format", nullable = false, length = 32)
    private String exportFormat;
}
