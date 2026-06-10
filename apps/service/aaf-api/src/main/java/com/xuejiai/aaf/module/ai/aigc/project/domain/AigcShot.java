package com.xuejiai.aaf.module.ai.aigc.project.domain;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** AIGC 分镜。 */
@Getter
@Setter
@Entity
@Table(name = "aigc_shot")
@SQLDelete(
        sql =
                "UPDATE aigc_shot SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ? AND version = ?")
public class AigcShot extends BaseEntity {

    /** 归属分镜规划 ID */
    @Column(name = "storyboard_id", nullable = false)
    private Long storyboardId;

    /** 镜号（排序） */
    @Column(name = "shot_no", nullable = false)
    private Integer shotNo;

    /** 镜头名称，如 Shot_LinBei_Entrance */
    @Column(name = "name", length = 200)
    private String name;

    /** 镜头描述，支持实体引用标记 @[名称](类型:ID)。 类型：e=元素/s=镜头/a=素材，示例：@[江澄](e:5) 坐在靠窗位 */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /** 台词/旁白 */
    @Column(name = "dialogue", columnDefinition = "TEXT")
    private String dialogue;

    /** 扩展属性：景别/运镜/情绪/滤镜等（JSON） */
    @Column(name = "properties", columnDefinition = "JSONB")
    private String properties;
}
