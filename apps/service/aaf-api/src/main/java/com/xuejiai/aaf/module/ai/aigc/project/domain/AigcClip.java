package com.xuejiai.aaf.module.ai.aigc.project.domain;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** 时间轴片段（引用素材，含时间位置）。 */
@Getter
@Setter
@Entity
@Table(name = "aigc_clip")
@SQLDelete(
        sql =
                "UPDATE aigc_clip SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ? AND version = ?")
public class AigcClip extends BaseEntity {

    /** 归属轨道 ID */
    @Column(name = "track_id", nullable = false)
    private Long trackId;

    /** 关联素材 ID */
    @Column(name = "asset_id")
    private Long assetId;

    /** 来源分镜 ID，NULL 表示手动添加 */
    @Column(name = "shot_id")
    private Long shotId;

    /** 在轨道上的起始位置（毫秒） */
    @Column(name = "position_ms", nullable = false)
    private Long positionMs = 0L;

    /** 素材入点（毫秒） */
    @Column(name = "in_ms", nullable = false)
    private Long inMs = 0L;

    /** 素材出点（毫秒） */
    @Column(name = "out_ms", nullable = false)
    private Long outMs = 0L;

    /** 扩展属性：speed/volume/filter/text 等（JSON） */
    @Column(name = "properties", columnDefinition = "JSONB")
    private String properties;
}
