package com.xuejiai.aaf.module.ai.aigc.video.domain;

import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * 视频模板实体。
 *
 * @author AaronZZH & Kiro
 */
@Getter
@Setter
@Entity
@Table(name = "video_template")
@SQLDelete(
        sql =
                "UPDATE video_template SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class VideoTemplate extends BaseEntity {

    /** 模板名称 */
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** 模板类型：INTRO/OUTRO/TRANSITION/SUBTITLE */
    @Column(name = "type", nullable = false, length = 30)
    private String type;

    /** 模板参数。 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "params", columnDefinition = "jsonb")
    private Map<String, Object> params;

    /** 预览视频的不可变媒体版本 ID。 */
    @Column(name = "preview_media_version_id")
    private Long previewMediaVersionId;

    /** 缩略图的不可变媒体版本 ID。 */
    @Column(name = "thumbnail_media_version_id")
    private Long thumbnailMediaVersionId;

    /** 所属用户 ID */
    @Column(name = "user_id")
    private Long userId;
}
