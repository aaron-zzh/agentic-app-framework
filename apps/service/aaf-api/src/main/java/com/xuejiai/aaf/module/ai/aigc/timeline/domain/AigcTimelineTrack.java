package com.xuejiai.aaf.module.ai.aigc.timeline.domain;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Timeline Composition 受控轨道。 */
@Getter
@Setter
@Entity
@Table(name = "aigc_timeline_track")
@SQLDelete(
        sql =
                "UPDATE aigc_timeline_track SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class AigcTimelineTrack extends BaseEntity {

    @Column(name = "composition_id", nullable = false)
    private Long compositionId;

    @Column(name = "track_type", nullable = false, length = 20)
    private String trackType;

    @Column(length = 100)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(nullable = false)
    private Boolean muted = false;

    @Column(nullable = false)
    private Boolean locked = false;
}
