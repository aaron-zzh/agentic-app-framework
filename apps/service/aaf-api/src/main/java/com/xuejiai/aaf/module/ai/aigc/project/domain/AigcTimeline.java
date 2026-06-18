package com.xuejiai.aaf.module.ai.aigc.project.domain;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** AIGC 视频剪辑时间轴。 */
@Getter
@Setter
@Entity
@Table(name = "aigc_timeline")
@SQLDelete(
        sql =
                "UPDATE aigc_timeline SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class AigcTimeline extends BaseEntity {

    /** 归属项目 ID */
    @Column(name = "project_id", nullable = false)
    private Long projectId;

    /** 来源分镜规划 ID，NULL 表示手动创建 */
    @Column(name = "storyboard_id")
    private Long storyboardId;

    /** 时间轴标题（正片/预告/花絮等） */
    @Column(name = "title", length = 200)
    private String title;

    /** 状态：DRAFT/EXPORTING/EXPORTED */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "DRAFT";

    /** 总时长（毫秒） */
    @Column(name = "duration_ms", nullable = false)
    private Long durationMs = 0L;

    /** 帧率，默认 30 */
    @Column(name = "fps", nullable = false)
    private Short fps = 30;

    /** 分辨率，如 1920x1080 */
    @Column(name = "resolution", nullable = false, length = 20)
    private String resolution = "1920x1080";
}
