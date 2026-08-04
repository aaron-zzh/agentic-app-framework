package com.xuejiai.aaf.module.ai.aigc.timeline.domain;

import java.math.BigDecimal;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Timeline Track 上只引用已采用输入的片段。 */
@Getter
@Setter
@Entity
@Table(name = "aigc_timeline_clip")
@SQLDelete(
        sql =
                "UPDATE aigc_timeline_clip SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class AigcTimelineClip extends BaseEntity {

    @Column(name = "track_id", nullable = false)
    private Long trackId;

    @Column(name = "media_version_id")
    private Long mediaVersionId;

    @Column(name = "source_object_id")
    private Long sourceObjectId;

    @Column(name = "source_object_version_id")
    private Long sourceObjectVersionId;

    @Column(name = "position_ms", nullable = false)
    private Long positionMs = 0L;

    @Column(name = "in_ms", nullable = false)
    private Long inMs = 0L;

    @Column(name = "out_ms", nullable = false)
    private Long outMs = 0L;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> properties;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> transition;

    @Column(precision = 8, scale = 4)
    private BigDecimal volume;
}
