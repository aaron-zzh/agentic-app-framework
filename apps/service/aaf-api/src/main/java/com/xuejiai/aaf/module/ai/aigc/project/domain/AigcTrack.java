package com.xuejiai.aaf.module.ai.aigc.project.domain;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** 时间轴轨道（VIDEO/AUDIO/SUBTITLE/STICKER）。 */
@Getter
@Setter
@Entity
@Table(name = "aigc_track")
@SQLDelete(
        sql =
                "UPDATE aigc_track SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ? AND version = ?")
public class AigcTrack extends BaseEntity {

    /** 归属时间轴 ID */
    @Column(name = "timeline_id", nullable = false)
    private Long timelineId;

    /** 轨道类型：VIDEO/AUDIO/SUBTITLE/STICKER */
    @Column(name = "type", nullable = false, length = 20)
    private String type;

    /** 排序序号 */
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    /** 是否静音 */
    @Column(name = "muted", nullable = false)
    private Boolean muted = false;

    /** 是否锁定 */
    @Column(name = "locked", nullable = false)
    private Boolean locked = false;
}
