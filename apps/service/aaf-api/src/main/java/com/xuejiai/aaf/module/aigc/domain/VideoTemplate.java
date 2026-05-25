package com.xuejiai.aaf.module.aigc.domain;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** 视频模板实体。 */
@Getter
@Setter
@Entity
@Table(name = "video_template")
@SQLDelete(sql = "UPDATE video_template SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ? AND version = ?")
public class VideoTemplate extends BaseEntity {

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** 模板类型：INTRO/OUTRO/TRANSITION/SUBTITLE */
    @Column(name = "type", nullable = false, length = 30)
    private String type;

    @Column(name = "params", columnDefinition = "TEXT")
    private String params;

    @Column(name = "preview_url", length = 500)
    private String previewUrl;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @Column(name = "user_id")
    private Long userId;
}
