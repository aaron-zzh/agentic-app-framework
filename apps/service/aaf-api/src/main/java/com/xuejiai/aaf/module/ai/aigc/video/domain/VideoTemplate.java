package com.xuejiai.aaf.module.ai.aigc.video.domain;

import org.hibernate.annotations.SQLDelete;

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

    /** 模板参数（JSON） */
    @Column(name = "params", columnDefinition = "TEXT")
    private String params;

    /** 预览视频 URL */
    @Column(name = "preview_url", length = 500)
    private String previewUrl;

    /** 缩略图 URL */
    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    /** 所属用户 ID */
    @Column(name = "user_id")
    private Long userId;
}
