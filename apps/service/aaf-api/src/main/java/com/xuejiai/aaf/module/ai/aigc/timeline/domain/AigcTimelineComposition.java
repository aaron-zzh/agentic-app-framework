package com.xuejiai.aaf.module.ai.aigc.timeline.domain;

import java.math.BigDecimal;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 视频交付物的轻量时间线组合根。 */
@Getter
@Setter
@Entity
@Table(name = "aigc_timeline_composition")
@SQLDelete(
        sql =
                "UPDATE aigc_timeline_composition SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class AigcTimelineComposition extends BaseEntity {

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "deliverable_object_id")
    private Long deliverableObjectId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "duration_ms", nullable = false)
    private Long durationMs = 0L;

    @Column(nullable = false, precision = 8, scale = 3)
    private BigDecimal fps = BigDecimal.valueOf(30);

    @Column(nullable = false)
    private Integer width = 1920;

    @Column(nullable = false)
    private Integer height = 1080;

    @Column(nullable = false, length = 32)
    private String status = "draft";

    @Column(name = "adopted_revision_no")
    private Integer adoptedRevisionNo;
}
